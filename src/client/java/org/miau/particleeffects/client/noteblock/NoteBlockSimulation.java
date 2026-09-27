package org.miau.particleeffects.client.noteblock;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.miau.particleeffects.client.display.ClientDisplayManager;
import org.miau.particleeffects.client.particle.ColoredEndRodParticle;
import org.miau.particleeffects.model.HorizontalCurve;
import org.miau.particleeffects.model.NoteBlockParams;

import java.util.ArrayList;
import java.util.List;

/**
 * 音符盒弹力球客户端模拟：接收服务端分配的目标，计算抛物线(竖直)+水平曲线轨迹。 *
 * 。tick 生成两批粒子：拖尾（glitter 星形粒子，静止在球的历史位置，生。拖尾长度形成彗尾。 * 与球头（spell 圆形光球粒子，尺寸更大、带。tick 速度，利用末地烛粒子“上一位置→当前位置。 * 的渲染插值让球头平滑滑动，形成“彗星的头”）。 *
 * 连击优化：落地前一 tick 即上报（预报告），服务端回发的下一次分配会先进。 * 本地待跳队列，落地瞬间立刻开始下一跳，消除两次跳跃之间的网络往返等待。 */
public final class NoteBlockSimulation {

    private NoteBlockParams params;
    private boolean enabled = false;
    private final List<Ball> balls = new ArrayList<>();

    public boolean enabled() {
        return enabled;
    }

    public void start(NoteBlockParams newParams) {
        this.params = newParams;
        this.enabled = true;
        this.balls.clear();
        for (int i = 0; i < newParams.ballCount(); i++) {
            balls.add(new Ball(i, newParams.center()));
        }
    }

    public void stop() {
        this.enabled = false;
        this.balls.clear();
        this.params = null;
    }

    public void activate(int ballIndex, Vec3d target, int note) {
        if (!enabled || params == null || ballIndex < 0 || ballIndex >= balls.size()) {
            return;
        }
        balls.get(ballIndex).queueJump(target, params.maxJumpHeight(), params.curve());
    }

    public void tick(ClientWorld world) {
        if (!enabled) {
            return;
        }
        List<Ball> landed = new ArrayList<>();
        for (Ball ball : balls) {
            ball.tick();
            ball.spawnParticles(world, params.trailCount());
            if (ball.consumeLandedFlag()) {
                landed.add(ball);
            }
        }
        for (Ball ball : landed) {
            ClientDisplayManager.reportBallLanded(ball.index);
        }
    }

    private static final class Ball {

        final int index;
        Vec3d pos;
        boolean idle = true;
        private Vec3d lastPos;
        private Vec3d start;
        private Vec3d target;
        private int jumpTicks;
        private int jumpElapsed;
        private boolean landedFlag;
        private boolean jumpReported;
        private HorizontalCurve lastCurve;
        private float lastHeight = 3f;
        private Vec3d pendingTarget;
        private float pendingHeight;
        private HorizontalCurve pendingCurve;

        Ball(int index, Vec3d initialPos) {
            this.index = index;
            this.pos = initialPos;
            this.lastPos = initialPos;
        }

        void queueJump(Vec3d newTarget, float maxHeight, HorizontalCurve curve) {
            if (idle) {
                beginJump(newTarget, maxHeight, curve);
            } else {
                this.pendingTarget = newTarget;
                this.pendingHeight = maxHeight;
                this.pendingCurve = curve;
            }
        }

        void beginJump(Vec3d newTarget, float maxHeight, HorizontalCurve curve) {
            this.start = this.pos;
            this.target = newTarget;
            this.lastCurve = curve;
            this.lastHeight = maxHeight;
            double dist = Math.hypot(target.x - start.x, target.z - start.z);
            int ticks = (int) Math.ceil(dist / 2.0);
            this.jumpTicks = Math.max(5, Math.min(40, ticks));
            this.jumpElapsed = 0;
            this.idle = false;
            this.landedFlag = false;
            this.jumpReported = false;
        }

        boolean consumeLandedFlag() {
            boolean was = landedFlag;
            landedFlag = false;
            return was;
        }

        void tick() {
            if (idle) {
                return;
            }
            lastPos = pos;
            jumpElapsed++;
            float t = Math.min(1f, (float) jumpElapsed / jumpTicks);
            pos = computePosition(t);
            if (t >= 1f) {
                pos = target;
                idle = true;
                boolean needReport = !jumpReported;
                if (pendingTarget != null) {
                    Vec3d nt = pendingTarget;
                    float nh = pendingHeight;
                    HorizontalCurve nc = pendingCurve;
                    pendingTarget = null;
                    beginJump(nt, nh, nc);
                }
                if (needReport) {
                    landedFlag = true;
                }
            } else if (!jumpReported && jumpElapsed >= jumpTicks - 1) {
                // 落地前一 tick 提前上报，让服务端分配的回包与落地重合，实现无缝连跳。
                jumpReported = true;
                landedFlag = true;
            }
        }

        void spawnParticles(ClientWorld world, int trailCount) {
            // 球仅在被激活时可见（跳跃中生成拖尾 + 球头）；静止时不再额外发光。
            if (idle) {
                return;
            }
            int maxAge = Math.max(4, Math.min(256, trailCount));
            ColoredEndRodParticle.spawnMoving(world, pos.x, pos.y, pos.z, 0, 0, 0, 0xFFF2E0, maxAge,
                    params.force());
            // 球头：圆球（spell 精灵）形状，尺寸大于拖尾星点，带本 tick 位移作为速度。
            // 渲染时利用“上一位置→当前位置”插值让球头平滑滑动。
            double vx = pos.x - lastPos.x;
            double vy = pos.y - lastPos.y;
            double vz = pos.z - lastPos.z;
            if (vx * vx + vy * vy + vz * vz > 1e-8) {
                ColoredEndRodParticle.spawnHead(world, pos.x, pos.y, pos.z, vx, vy, vz, 0xFFF2E0, 2.0f, 2,
                        params.force());
            }
        }

        private Vec3d computePosition(float t) {
            double baseX = lerp(start.x, target.x, t);
            double baseZ = lerp(start.z, target.z, t);

            double dx = target.x - start.x;
            double dz = target.z - start.z;
            double len = Math.hypot(dx, dz);
            if (len > 1e-4 && lastCurve != HorizontalCurve.LINE) {
                double nx = -dz / len;
                double nz = dx / len;
                double amp = Math.max(1.0, Math.min(6.0, len * 0.5));
                double offset;
                if (lastCurve == HorizontalCurve.ARC) {
                    offset = amp * (1.0 - Math.cos(2 * Math.PI * t)) / 2.0;
                } else {
                    offset = amp * Math.sin(2 * Math.PI * t);
                }
                baseX += nx * offset;
                baseZ += nz * offset;
            }

            double y0 = start.y;
            double y1 = target.y;
            // 抛物线最高点 = maxJumpHeight（配置的跳动最大高度），竖直为固定样式的抛物线。
            double y = lerp(y0, y1, t) + 4.0 * lastHeight * t * (1.0 - t);
            if (y < Math.min(y0, y1)) {
                y = Math.min(y0, y1);
            }
            return new Vec3d(baseX, y, baseZ);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
