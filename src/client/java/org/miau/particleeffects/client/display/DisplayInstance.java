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
    /**
     * 是否刚推进了一个游戏 tick。
     *
     * <p>帧驱动实例每帧都会重生粒子；粒子速度必须按「每 tick 位移」给出，旧粒子才能在寿命内
     * 滑到新位置。子类在生成粒子时消费该标志，用它按 tick 采样位移，避免把帧间位移当速度
     * 造成拖影。</p>
     */
    protected boolean tickAdvanced = true;
    /**
     * 粒子速度补偿系数。
     *
     * <p>粒子寿命 2 tick、摩擦 0.91，两 tick 的总位移为 {@code v * (1 + 0.91)}。
     * 传入「一个游戏 tick 的位移」乘以该系数，旧粒子才会在寿命结束时正好滑到新位置，
     * 否则会拖在后面形成拖影。</p>
     */
    protected static final double PARTICLE_VELOCITY_SCALE = 1.0 / (1.0 + 0.91);

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
        tickAdvanced = true;
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
     *
     * <p>插值系数统一先钳到 [0,1]：tick 冻结、replay 变速或补 tick 时原版可能给出
     * 越界值，直接用会让进度瞬间跳变/回退。</p>
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
        return Math.min(1f, (tickCounter + clampPartial(partialTick)) / duration);
    }

    /** 把插值系数钳到 [0,1]，并挡掉 NaN。 */
    protected static float clampPartial(float partialTick) {
        if (!(partialTick >= 0f)) {
            return 0f;
        }
        return Math.min(1f, partialTick);
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
            t = (currentTick + clampPartial(partialTick)) / moveTicks;
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