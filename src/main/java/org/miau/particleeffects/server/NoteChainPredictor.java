package org.miau.particleeffects.server;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * 音符链预测器（服务端）。
 *
 * 红石音乐通常是“链式结构”：音符盒按固定顺序被依次激活，同一个音符盒后面总是
 * 跟着固定（或极少数几个）后继音符盒。这里不解析红石布局（中继器朝向、线缆走向
 * 会随机器结构变化，且本项目要同时兼容多个 MC 版本），而是直接从观测到的激活序列
 * 里学习“A 的下一个是谁、隔多少 tick”，从而在 A 这次激活的当下就能把 B 提前下发，
 * 让弹力球提前起飞、正好落到 B 上，跟上歌曲节奏。
 *
 * 说明：视频/歌单一般会循环，或至少会重复经过同一段链路，因此第二遍起即可稳定预测；
 * 第一遍为兜底（客户端按距离自行估算，见 NoteBlockSimulation）。
 */
public final class NoteChainPredictor {

    /** 相邻两次激活的最大间隔（tick）。超过视为链路断开，不作为后继关系学习。 */
    private static final int MAX_GAP_TICKS = 40;
    /** 后继关系需被观测到的次数，达到后才认为可信。 */
    private static final int MIN_CONFIDENCE = 1;
    /** 学习表容量上限，超出直接重学，避免无限增长。 */
    private static final int MAX_ENTRIES = 512;
    /** 提前量（tick）的上下限。 */
    private static final int MIN_LEAD_TICKS = 1;
    private static final int MAX_LEAD_TICKS = 40;

    /** 预测结果：下一个将被激活的音符盒坐标 + 到它激活还剩的 tick 数。 */
    public record Prediction(BlockPos pos, int leadTicks) {
    }

    private static final class Successor {
        final BlockPos pos;
        int count;
        int gap;
        long order;

        Successor(BlockPos pos) {
            this.pos = pos;
        }
    }

    /** from(位置) → (to(位置) → 统计)。 */
    private final Map<Long, Map<Long, Successor>> links = new HashMap<>();
    private BlockPos anchorPos;
    private long anchorTick = Long.MIN_VALUE;

    /** 记录一次音符盒激活（pos 为音符盒方块坐标）。 */
    public void observe(BlockPos pos, long tick) {
        if (anchorPos == null) {
            anchorPos = pos;
            anchorTick = tick;
            return;
        }
        if (tick <= anchorTick) {
            // 同一 tick 内的多个音符盒（和弦）：保留本 tick 的第一个作为链路锚点。
            return;
        }
        long gap = tick - anchorTick;
        if (gap <= MAX_GAP_TICKS) {
            record(anchorPos, pos, (int) gap, tick);
        }
        anchorPos = pos;
        anchorTick = tick;

        if (links.size() > MAX_ENTRIES) {
            links.clear();
        }
    }

    private void record(BlockPos from, BlockPos to, int gap, long order) {
        Map<Long, Successor> successors = links.computeIfAbsent(from.asLong(), k -> new HashMap<>());
        Successor s = successors.computeIfAbsent(to.asLong(), k -> new Successor(to));
        s.count++;
        s.gap = gap;
        s.order = order;
    }

    /** 预测 pos 的下一个音符盒；无足够把握时返回 null（客户端走兜底估算）。 */
    public Prediction predict(BlockPos pos) {
        Map<Long, Successor> successors = links.get(pos.asLong());
        if (successors == null || successors.isEmpty()) {
            return null;
        }
        Successor best = null;
        for (Successor s : successors.values()) {
            if (best == null || s.count > best.count || (s.count == best.count && s.order > best.order)) {
                best = s;
            }
        }
        if (best == null || best.count < MIN_CONFIDENCE) {
            return null;
        }
        // 补偿半程网络/调度延迟：多减 1 tick，宁可略早到也不要晚到。
        int lead = Math.max(MIN_LEAD_TICKS, Math.min(MAX_LEAD_TICKS, best.gap - 1));
        return new Prediction(best.pos, lead);
    }
}
