package org.miau.particleeffects.client.render;

import net.minecraft.client.Minecraft;
import org.miau.particleeffects.client.display.ClientDisplayBackend;

/**
 * 帧驱动：把特效与弹力球的位置推进从原版 20Hz 的客户端 tick 提升到渲染帧率（约 60FPS）。
 *
 * 原版粒子只在 20Hz 的客户端 tick 上更新位置，渲染时仅做两 tick 之间的直线插值，
 * 因此高速移动的弹力球会出现明显的折线/多边形感。这里在每个渲染帧回调中用
 * 「已过 tick 数 + 帧间插值系数」重新求位置并生成粒子，使轨迹按帧连续。
 *
 * 钩子按版本分支：1.21.x 用 HUD 渲染回调；26.1+ 的 HudRenderCallback 已被移除，
 * 改用世界渲染提取事件（同时不受 HUD 显隐影响）。
 */
public final class FrameDriver {

    /** 同一渲染帧可能被多个回调命中，用 2ms 阈值去重，避免重复生成粒子。 */
    private static final long FRAME_DEDUP_NANOS = 2_000_000L;

    private static long lastFrameNanos;

    private FrameDriver() {
    }

    public static void init() {
        /*? if >=26.1 {*/
        net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents.END_EXTRACTION
                .register(context -> onFrame());
        /*?} else {*/
        /*net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT
                .register((context, tickCounter) -> onFrame());*/
        /*?}*/
    }

    private static void onFrame() {
        long now = System.nanoTime();
        if (now - lastFrameNanos < FRAME_DEDUP_NANOS) {
            return;
        }
        lastFrameNanos = now;

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.isPaused()) {
            return;
        }
        // 插值系数必须取原版 DeltaTracker 的「游戏时间」残差：它会按当前 tick rate 换算，
        // 用固定的 50ms/tick 自行推算时，/tick rate 一改就会把 tick 边界算错，
        // 导致位置推进与 tick 驱动的动画不同步（高 tick rate 卡顿、低 tick rate 瞬移）。
        float partialTick;
        /*? if >=1.21.4 {*/
        partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        /*?} else {*/
        /*partialTick = client.getTimer().getGameTimeDeltaPartialTick(true);*/
        /*?}*/
        if (!(partialTick >= 0f)) {
            partialTick = 0f;
        }
        ClientDisplayBackend.instance().frame(client.level, partialTick);
    }
}
