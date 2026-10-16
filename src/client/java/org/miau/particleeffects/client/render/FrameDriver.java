package org.miau.particleeffects.client.render;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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

    /** 1 个游戏 tick = 50ms。 */
    private static final double NANOS_PER_TICK = 50_000_000.0;
    /** 同一渲染帧可能被多个回调命中，用 2ms 阈值去重，避免重复生成粒子。 */
    private static final long FRAME_DEDUP_NANOS = 2_000_000L;
    /** 插值系数上限：超过说明 tick 边界时钟异常（长时间卡顿/暂停），退回 tick 起点避免瞬移。 */
    private static final double MAX_PARTIAL = 1.5;

    private static long lastTickNanos;
    private static long lastFrameNanos;

    private FrameDriver() {
    }

    public static void init() {
        // tick 末尾即下一个 tick 的起点，此刻插值系数约为 0。
        ClientTickEvents.END_CLIENT_TICK.register(client -> lastTickNanos = System.nanoTime());
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
        double partialTick = (now - lastTickNanos) / NANOS_PER_TICK;
        if (partialTick < 0 || partialTick > MAX_PARTIAL) {
            partialTick = 0;
        }
        ClientDisplayBackend.instance().frame(client.level, (float) partialTick);
    }
}
