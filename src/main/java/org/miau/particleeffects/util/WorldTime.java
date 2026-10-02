package org.miau.particleeffects.util;

import net.minecraft.world.level.Level;

/**
 * 世界时间获取的跨版本适配。
 *
 * Minecraft 在不同版本对“世界时间 tick”的 API 做过多次重构：
 *  - 1.21.1 ~ 1.21.8 ：{@code Level.getGameTime()}
 *  - 1.21.9 ~ 1.21.x ：{@code Level.getDayTime()}（getGameTime 被移除）
 *  - 26.1+           ：{@code Level.getOverworldClockTime()}（世界时钟系统）
 *
 * 彩虹相位只依赖一个单调递增的 tick 值，故此处统一取其一。
 */
public final class WorldTime {

    private WorldTime() {
    }

    public static long ticks(Level level) {
        /*? if >=26.1 {*/
        return level.getOverworldClockTime();
        /*?} elif >=1.21.9 {*/
        /*return level.getDayTime();
        *//*?} else {*/
        /*return level.getGameTime();
        *//*?}*/
    }
}