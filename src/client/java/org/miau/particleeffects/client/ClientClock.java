package org.miau.particleeffects.client;

/**
 * 客户端单调 tick 时钟。
 *
 * <p>不能用世界时间（{@code getDayTime} / {@code getOverworldClockTime}）驱动动画：
 * {@code /time set} 会让它整体跳变，而关闭日夜循环（时间固定）时它完全不推进，
 * 会让弹力球、彩虹色等动画直接卡死。这里每客户端 tick 自增一次，
 * 暂停（单人 Esc）时与世界冻结保持一致、不推进。</p>
 */
public final class ClientClock {

    private static long ticks;

    private ClientClock() {
    }

    public static long ticks() {
        return ticks;
    }

    /** 由客户端 tick 事件推进；调用方负责在暂停时跳过。 */
    public static void advance() {
        ticks++;
    }
}
