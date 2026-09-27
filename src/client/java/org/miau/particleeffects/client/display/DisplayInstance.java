package org.miau.particleeffects.client.display;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.Easing;
import org.miau.particleeffects.animation.FadeOption;
import org.miau.particleeffects.model.MoveSpec;
import org.miau.particleeffects.model.Orientation;

/**
 * 显示实例基类：管。PENDING→ENTER→DISPLAY→EXIT→DELETED 状态机。 * 子类实现 {@link #spawnParticles(ClientWorld)} 来根据当前阶段和进度生成末地烛粒子。 */
public abstract class DisplayInstance {

    public enum Phase {
        PENDING,
        ENTER,
        DISPLAY,
        EXIT,
        DELETED
    }

    protected final Vec3d pos;
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

    protected DisplayInstance(Vec3d pos, Orientation orientation,
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

    protected float phaseProgress() {
        int duration = switch (phase) {
            case ENTER -> enterTicks;
            case DISPLAY -> durationTicks;
            case EXIT -> exitTicks;
            default -> 1;
        };
        if (duration <= 0) {
            return 1f;
        }
        return Math.min(1f, (float) tickCounter / duration);
    }

    protected float easedProgress() {
        return curve.apply(phaseProgress());
    }

    protected Vec3d computeMoveOffset(int currentTick, float partialTick) {
        if (move == null || phase != Phase.DISPLAY) {
            return Vec3d.ZERO;
        }
        int moveTicks = move.durationTicks();
        if (moveTicks <= 0) {
            return Vec3d.ZERO;
        }
        float t;
        if (currentTick >= moveTicks) {
            t = 1f;
        } else {
            t = (currentTick + partialTick) / moveTicks;
        }
        float eased = move.curve().apply(Math.min(1f, Math.max(0f, t)));
        return move.delta().multiply(eased);
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
            alpha = easedProgress();
        } else if (phase == Phase.EXIT && fade.fadeOut()) {
            alpha = 1f - easedProgress();
        }
        return alpha;
    }

    /**
     * 。tick 生成粒子到世界。子类根据当前阶段进度生成粒子（end_rod 粒子）。     *
     * @param world 客户端世界（用于 addParticleClient。     */
    public abstract void spawnParticles(ClientWorld world);
}