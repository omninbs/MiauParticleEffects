package org.miau.particleeffects.client.display;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.Easing;
import org.miau.particleeffects.animation.FadeOption;
import org.miau.particleeffects.model.MoveSpec;
import org.miau.particleeffects.model.Orientation;

/**
 * 显示实例基类：管。PENDING→ENTER→DISPLAY→EXIT→DELETED 状态机。 * 子类实现 {@link #spawnParticles(ClientLevel)} 来根据当前阶段和进度生成末地烛粒子。 */
public abstract class DisplayInstance {

    public enum Phase {
        PENDING,
        ENTER,
        DISPLAY,
        EXIT,
        DELETED
    }

    protected final Vec3 pos;
    protected final Orientation orientation;
    protected final int durationTicks;
    protected final int enterTicks;
    protected final int exitTicks;
    protected final int delayTicks;
    protected final Easing curve;
    protected final AnimationSet entryAnim;
    protected final AnimationSet exitAnim;
    protected final FadeOption fade;
    protected final MoveSpec move;
    protected final String id;

    protected int tickCounter = 0;
    protected Phase phase = Phase.PENDING;
    /** 本次生成粒子所用到的帧间插值系数（tick 驱动时为 0）。 */
    protected float framePartial = 0f;

    protected DisplayInstance(Vec3 pos, Orientation orientation,
                               int durationTicks, int enterTicks, int exitTicks, int delayTicks,
                               Easing curve, AnimationSet entryAnim, AnimationSet exitAnim,
                               FadeOption fade, MoveSpec move, String id) {
        this.pos = pos;
        this.orientation = orientation;
        this.durationTicks = durationTicks;
        this.enterTicks = enterTicks;
        this.exitTicks = exitTicks;
        this.delayTicks = delayTicks;
        this.curve = curve;
        this.entryAnim = entryAnim;
        this.exitAnim = exitAnim;
        this.fade = fade;
        this.move = move;
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Phase phase() {
        return phase;
    }

    public boolean isDeleted() {
        return phase == Phase.DELETED;
    }

    public void forceExit() {
        if (phase == Phase.DELETED) {
            return;
        }
        if (phase == Phase.EXIT || exitTicks == 0) {
            phase = Phase.DELETED;
            return;
        }
        tickCounter = 0;
        phase = Phase.EXIT;
    }

    public void tick() {
        if (phase == Phase.DELETED) {
            return;
        }
        tickCounter++;
        switch (phase) {
            case PENDING -> {
                if (tickCounter >= delayTicks) {
                    tickCounter = 0;
                    phase = enterTicks > 0 ? Phase.ENTER : Phase.DISPLAY;
                }
            }
            case ENTER -> {
                if (tickCounter >= enterTicks) {
                    tickCounter = 0;
                    phase = Phase.DISPLAY;
                }
            }
            case DISPLAY -> {
                if (tickCounter >= durationTicks) {
                    tickCounter = 0;
                    phase = exitTicks > 0 ? Phase.EXIT : Phase.DELETED;
                }
            }
            case EXIT -> {
                if (tickCounter >= exitTicks) {
                    phase = Phase.DELETED;
                }
            }
            default -> {
            }
        }
    }

    /**
     * 阶段进度。partialTick 为当前渲染帧相对上一个 tick 的插值系数，
     * 使动画在渲染帧上连续推进（约 60FPS），而不是每 tick 跳一格。
     */
    protected float phaseProgress(float partialTick) {
        int duration = switch (phase) {
            case ENTER -> enterTicks;
            case DISPLAY -> durationTicks;
            case EXIT -> exitTicks;
            default -> 1;
        };
        if (duration <= 0) {
            return 1f;
        }
        return Math.min(1f, (tickCounter + partialTick) / duration);
    }

    protected float phaseProgress() {
        return phaseProgress(0f);
    }

    protected float easedProgress(float partialTick) {
        return curve.apply(phaseProgress(partialTick));
    }

    protected float easedProgress() {
        return easedProgress(0f);
    }

    /**
     * 是否按渲染帧率生成粒子：
     * true  → 由渲染帧回调驱动（可拿到插值系数，动画/轨迹更平滑，按 60FPS 生成）；
     * false → 仍由 20Hz 的客户端 tick 驱动。
     */
    public boolean frameDriven() {
        return false;
    }

    protected Vec3 computeMoveOffset(int currentTick, float partialTick) {
        if (move == null || phase != Phase.DISPLAY) {
            return Vec3.ZERO;
        }
        int moveTicks = move.durationTicks();
        if (moveTicks <= 0) {
            return Vec3.ZERO;
        }
        float t;
        if (currentTick >= moveTicks) {
            t = 1f;
        } else {
            t = (currentTick + partialTick) / moveTicks;
        }
        float eased = move.curve().apply(Math.min(1f, Math.max(0f, t)));
        return move.delta().scale(eased);
    }

    protected float computeFadeAlpha() {
        if (phase == Phase.DELETED) {
            return 0f;
        }
        if (phase == Phase.PENDING) {
            return 0f;
        }
        float alpha = 1f;
        if (phase == Phase.ENTER && fade.fadeIn()) {
            alpha = easedProgress(framePartial);
        } else if (phase == Phase.EXIT && fade.fadeOut()) {
            alpha = 1f - easedProgress(framePartial);
        }
        return alpha;
    }

    /**
     * 生成粒子到世界。子类根据当前阶段进度生成粒子（end_rod 粒子）。
     *
     * @param world       客户端世界
     * @param partialTick 帧间插值系数（由 {@link #frameDriven()} 决定是否有效）
     */
    public abstract void spawnParticles(ClientLevel world, float partialTick);
}