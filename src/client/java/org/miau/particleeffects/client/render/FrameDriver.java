package org.miau.particleeffects.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.miau.particleeffects.client.display.ClientDisplayBackend;

/**
 * 帧驱动：把弹力球 / 歌词链的**游戏时间**按渲染帧率连续推进。
 *
 * <h2>为什么不用「tick 计数 + partialTick 插值」</h2>
 * <p>那种做法的运动是「每 tick 跳一格，再用 partialTick 补间」。只要 tick 计数与插值系数
 * 的来源/相位有一点不一致（服务端时间包覆盖 gameTime、一帧跑多个 tick、事件顺序差异），
 * 补间就会漏一格或倒退一格 —— 表现就是「移动一顿一顿」。而且 tick rate 变化时，
 * 每 tick 的持续时间变了，阶梯的疏密也跟着变，看起来更明显。</p>
 *
 * <h2>现在的做法</h2>
 * <p>用原版 {@code DeltaTracker#getGameTimeDeltaTicks()}：它就是
 * 「本帧真实毫秒 ÷ 当前每 tick 毫秒数」，由 {@code TickRateManager} 提供毫秒值。
 * 因此它同时满足：</p>
 * <ul>
 *   <li><b>/tick rate 变速</b>：rate=2 时每帧增量翻倍，rate=0.5 时减半；</li>
 *   <li><b>游戏暂停 / /tick freeze / rate=0</b>：这里直接给 0，时间轴冻结；</li>
 *   <li><b>/time set</b>：与日夜时钟无关；</li>
 *   <li>累加后是一条**连续曲线**，渲染帧之间没有任何 tick 阶梯。</li>
 * </ul>
 *
 * <p>{@code partialTick} 仍然保留给「以 tick 为单位」的显示动画（文字入场/出场等），
 * 它们是离散动画，不需要连续时间。</p>
 */
public final class FrameDriver {

    /** 同一渲染帧可能被多个回调命中，用 1ms 阈值去重，避免重复生成粒子。 */
    private static final long FRAME_DEDUP_NANOS = 1_000_000L;
    /** 单帧最多推进的游戏时间：严重掉帧（窗口最小化）后不让小球一口气瞬移一大段。 */
    private static final float MAX_FRAME_DELTA_TICKS = 10f;

    private static long lastFrameNanos;
    /** 上一次真正处理渲染帧的时间戳：用来把真实帧时长换算成游戏时间。 */
    private static long lastAdvanceNanos;

    private FrameDriver() {
    }

    public static void init() {
        /*? if >=26.1 {*/
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION
                .register(context -> onFrame());
        /*?}*/
        /*? if >=1.21.11 && <26.1 {*/
        /*net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.END_EXTRACTION
                .register(context -> onFrame());*/
        /*?}*/
        /*? if <1.21.11 {*/
        /*net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.END
                .register(context -> onFrame());*/
        /*?}*/
    }

    private static void onFrame() {
        long now = System.nanoTime();
        if (now - lastFrameNanos < FRAME_DEDUP_NANOS) {
            return;
        }
        lastFrameNanos = now;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            return;
        }
        // 不能因为 isPaused() 直接返回：暂停时世界仍在渲染，而粒子寿命只有 2 tick，
        // 一旦停止撒点，40ms 后小球就整体消失。这里只是把「时间增量」置 0，位置静止、继续撒点。
        ClientLevel level = client.level;
        float partialTick = 0f;
        float deltaTicks = 0f;
        if (isGameTimeRunning(client, level)) {
            partialTick = gamePartialTick(client);
            deltaTicks = frameGameTicks(level, now);
        } else {
            // 暂停/freeze：不推进时间，但下一帧的有效时长要从「恢复的那一刻」开始算。
            lastAdvanceNanos = now;
        }
        ClientDisplayBackend.instance().frame(level, partialTick, deltaTicks);
    }

    /** 游戏时间是否在推进：暂停、{@code /tick freeze}、{@code /tick rate 0} 都为 false。 */
    private static boolean isGameTimeRunning(Minecraft client, ClientLevel level) {
        if (client.isPaused()) {
            return false;
        }
        return level.tickRateManager().runsNormally();
    }

    /**
     * 本帧流逝的游戏时间（tick，含小数）= 真实帧时长 ÷ 当前每 tick 毫秒数。
     *
     * <p>每 tick 毫秒数来自 {@code TickRateManager}，所以 {@code /tick rate 2} 时增量翻倍、
     * {@code /tick rate 0.5} 时减半，与音符盒/红石完全同步。</p>
     *
     * <p>刻意不用 {@code DeltaTracker#getGameTimeDeltaTicks()}：那个字段只在
     * {@code Minecraft.runTick(renderLevel=true)} 的帧刷新，若某帧没刷新就会把上一帧的增量
     * **重复累加**，表现为小球突然向前顿一下。这里用帧时间戳自己算，永远不会重复。</p>
     */
    private static float frameGameTicks(ClientLevel level, long now) {
        long previous = lastAdvanceNanos;
        lastAdvanceNanos = now;
        if (previous == 0L) {
            return 0f;
        }
        double frameMillis = (now - previous) / 1_000_000.0;
        float millisPerTick = level.tickRateManager().millisecondsPerTick();
        if (!(millisPerTick > 0f) || !(frameMillis > 0.0)) {
            return 0f;
        }
        double delta = frameMillis / millisPerTick;
        if (!(delta > 0.0)) {
            return 0f;
        }
        return (float) Math.min(delta, MAX_FRAME_DELTA_TICKS);
    }

    /** 游戏刻内的插值系数（0~1），给「以 tick 为单位」的离散动画用。 */
    private static float gamePartialTick(Minecraft client) {
        float partial;
        /*? if >=1.21.4 {*/
        partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        /*?} else {*/
        /*partial = client.getTimer().getGameTimeDeltaPartialTick(true);*/
        /*?}*/
        if (!(partial >= 0f)) {
            return 0f;
        }
        return Math.min(1f, partial);
    }
}
