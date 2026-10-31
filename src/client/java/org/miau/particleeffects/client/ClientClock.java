package org.miau.particleeffects.client;

/**
 * 客户端游戏刻计数：每发生一个游戏 tick 就 +1，是弹力球 / 歌词链等「按时间轴播放」
 * 内容的唯一时间锚点。
 *
 * <h2>为什么不能用世界时间</h2>
 * <ul>
 *   <li><b>{@code getDayTime()} / {@code getOverworldClockTime()}</b>：日夜时钟。
 *       {@code /time set} 会让它整体跳变；关闭日夜循环（时间固定）时它根本不推进，
 *       动画会卡死。</li>
 *   <li><b>{@code getGameTime()}</b>：看着像刻计数，但客户端每收到一次
 *       {@code ClientboundSetTimePacket}（服务端每 tick 广播）就会被
 *       {@code ClientLevel#setTimeFromServer} <b>无条件覆盖</b>成稍旧的服务端值。
 *       网络延迟一抖动，相邻两次采样之间的增量就变成 0 或 2 —— 那个 tick 会少撒/多撒
 *       一批粒子，小球就会「跳一格」。这就是「流畅一会突然瞬移」的来源。</li>
 * </ul>
 *
 * <p>客户端 tick 事件只在游戏真正推进 tick 时触发（暂停、{@code /tick freeze}、
 * {@code /tick rate 0} 都不触发），因此这个计数天然满足：</p>
 * <ul>
 *   <li>每个游戏 tick <b>恒定 +1</b>，不受任何时间同步包影响；</li>
 *   <li>{@code /tick rate} 变化时事件触发频率同步变化，每真实秒的增量 = tick rate；</li>
 *   <li>暂停 / {@code /tick freeze} 时不推进 → 动画冻结；</li>
 *   <li>{@code /time set}、维度切换完全无关。</li>
 * </ul>
 */
public final class ClientClock {

    private static long ticks;

    private ClientClock() {
    }

    public static long ticks() {
        return ticks;
    }

    /**
     * 由客户端 tick 事件推进：一个游戏 tick 一次。
     *
     * <p>不做 {@code isPaused()} 判断：tick 事件本身就代表「这一 tick 真的发生了」，
     * 而 {@code isPaused()} 在多人/回放场景并不可靠，判断错了会让时间轴凭空冻结。</p>
     */
    public static void advance() {
        ticks++;
    }
}
