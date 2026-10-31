package org.miau.particleeffects.client.text;

import org.miau.particleeffects.client.ClientClock;
import org.miau.particleeffects.client.display.ClientDisplayBackend;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.TextChainPayload;

import java.util.List;

/**
 * 一条歌词链的客户端播放器：本地按整条时间轴推进，与红石、服务端逐句调度完全解耦。
 *
 * <p>每段歌词的 {@code startTick} 是相对「命令方块执行那一刻」的绝对 tick。播放器只在
 * 到点时才创建 {@link org.miau.particleeffects.client.display.TextInstance}，因此一条链
 * 下发 60 句歌词也不会一次性栅格化 60 段文字，更不会同时存在 60 个实例。</p>
 *
 * <p>时间用客户端单调时钟推进（见 {@link ClientClock}）：世界时间固定或 {@code /time set}
 * 都不会影响节奏，卡顿后的超大跳变按 1 tick 处理，避免整首歌被瞬间播完。</p>
 */
public final class TextChainPlayer {

    /** 相邻两次 tick 之间允许计入时间轴的最大增量，超出按 1 tick 处理。 */
    private static final long MAX_TICK_DELTA = 20L;

    private final String id;
    private final List<TextChainPayload.Entry> entries;
    private final int linger;
    /** 整条链的总时长：最后一段的开始时刻 + 它的显示时长。 */
    private final long totalTicks;

    private long lastClockTime;
    private boolean clockInitialised;
    private long elapsedTicks;
    private int next;
    private boolean removed;

    public TextChainPlayer(TextChainPayload payload) {
        this.id = payload.id();
        this.entries = List.copyOf(payload.entries());
        this.linger = payload.linger();
        long total = 0;
        for (TextChainPayload.Entry entry : entries) {
            long end = entry.startTick() + entry.params().totalTicks();
            if (end > total) {
                total = end;
            }
        }
        this.totalTicks = total;
    }

    public String id() {
        return id;
    }

    public boolean isRemoved() {
        return removed;
    }

    /** 推进时间轴：到点的歌词立刻实例化，其余等待。 */
    public void tick() {
        long now = ClientClock.ticks();
        if (!clockInitialised) {
            lastClockTime = now;
            clockInitialised = true;
            return;
        }
        long delta = now - lastClockTime;
        lastClockTime = now;
        if (delta < 0 || delta > MAX_TICK_DELTA) {
            delta = 1;
        }
        this.elapsedTicks += delta;

        while (next < entries.size() && entries.get(next).startTick() <= elapsedTicks) {
            TextChainPayload.Entry entry = entries.get(next);
            String itemId = id + "#" + next;
            next++;
            ClientDisplayBackend.instance().showChainText(entry.params().withId(itemId));
        }

        if (linger >= 0 && next >= entries.size() && elapsedTicks >= totalTicks + linger) {
            removed = true;
        }
    }

    /** 结束整条链：把这条链已经/将要显示的文字实例全部退场。 */
    public void clearInstances() {
        ClientDisplayBackend.instance().clearByPrefix(id + "#");
    }
}
