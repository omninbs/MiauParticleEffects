package org.miau.particleeffects.client.text;

import net.minecraft.client.multiplayer.ClientLevel;
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
 * <p>时间轴由客户端游戏刻推进（{@link ClientClock}，每 tick 恒 +1）：/tick rate 变速时
 * 事件频率同步变化、暂停与 {@code /tick freeze} 时不推进、{@code /time set} 完全无关。</p>
 */
public final class TextChainPlayer {

    /** 兜底补偿时单次允许推进的最大刻数（跳变按 1 处理）。 */
    private static final long MAX_TICK_STEP = 20L;

    private final String id;
    private final List<TextChainPayload.Entry> entries;
    private final int linger;
    /** 整条链的总时长：最后一段的开始时刻 + 它的显示时长。 */
    private final long totalTicks;

    /** 已播放的游戏时间（tick）。 */
    private double elapsedTicks;
    /** 上一次 tick 时的客户端游戏刻（每 tick 恒 +1）。 */
    private long lastTickTime = Long.MIN_VALUE;
    /** 是否已由服务端进度驱动（true 时不再用客户端 tick 兜底）。 */
    private boolean progressDriven;
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

    /**
     * 渲染帧侧：字幕只在游戏刻上推进，这里不做任何事（保留接口以对齐显示后端的帧回调）。
     */
    public void frame(ClientLevel world, float deltaTicks) {
        // 无需处理：时间轴由 tick 推进，字幕的显隐本身以 tick 为单位。
    }

    /**
     * 服务端广播的链进度：字幕到点时刻改由服务端 tick 决定，
     * 这样 {@code /tick rate} 变速、暂停、{@code /tick freeze}、回放都与音乐对齐。
     */
    public void onServerProgress(float elapsed) {
        this.progressDriven = true;
        this.elapsedTicks = elapsed;
        dispatchDueEntries();
        checkExpiry();
    }

    /** 没有服务端进度时（例如服务端未装/未同步）的兜底：按客户端游戏刻推进。 */
    public void tick(ClientLevel world) {
        if (progressDriven) {
            return;
        }
        long now = ClientClock.ticks();
        long delta = lastTickTime == Long.MIN_VALUE ? 0 : now - lastTickTime;
        lastTickTime = now;
        if (delta <= 0 || delta > MAX_TICK_STEP) {
            delta = 1;
        }
        this.elapsedTicks += delta;
        dispatchDueEntries();
        checkExpiry();
    }

    private void checkExpiry() {
        if (linger >= 0 && next >= entries.size() && elapsedTicks >= totalTicks + linger) {
            removed = true;
        }
    }

    /** 把已经到点的字幕实例化；时间轴跨过很多段时跳过已播完的段。 */
    private void dispatchDueEntries() {
        while (next < entries.size() && entries.get(next).startTick() <= elapsedTicks) {
            TextChainPayload.Entry entry = entries.get(next);
            String itemId = id + "#" + next;
            next++;
            if (entry.startTick() + entry.params().totalTicks() <= elapsedTicks) {
                continue;
            }
            ClientDisplayBackend.instance().showChainText(entry.params().withId(itemId));
        }
    }

    /** 结束整条链：把这条链已经/将要显示的文字实例全部退场。 */
    public void clearInstances() {
        ClientDisplayBackend.instance().clearByPrefix(id + "#");
    }
}
