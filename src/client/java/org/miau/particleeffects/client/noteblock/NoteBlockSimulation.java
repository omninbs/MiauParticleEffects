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
 * 音符盒弹力球客户端模拟：接收服务端分配的目标，计算抛物线（竖直，固定样式）
 * + 水平运动曲线（line/arc/sine）的独立控制轨迹。
 *
 * 视觉（优化目标：显眼、区别明显、60fps 流畅）：
 *  - 拖尾：沿弹力球本 tick 走过的路径段按 0.35 方块间距密集撒星点，形成连续彗尾（不再断点）；
 *    星点静止、自然寿命 = trail（到达上限即“删除最早 + 后半生淡出”，即原设计的“贪吃蛇拖尾”）。
 *    拖尾密度只由路径长度决定，不被 trail 的“粒子数量”上限压制。
 *  - 球头：放大加亮的圆形光核 + 头后两层柔和光晕；带本 tick 位移作为速度，
 *    利用 MC 粒子“上一帧位置→当前帧位置”的渲染插值让球头逐帧平滑滑动（60fps 无跳格）。
 *  - 空闲：在最近落点保留一小团柔和光晕，便于持续追踪弹力球位置。
 *  - 多球区分：每颗弹力球使用不同的高亮颜色，避免多球同跳/共落时不可分辨。
 *
 * 连击优化：落地前一 tick 即上报（预报告），服务端回发的下一次分配会先进本地待跳队列，
 * 落地瞬间立刻开始下一跳，消除两次跳跃之间的网络往返等待。
 */
public final class NoteBlockSimulation {

    /** 拖尾撒点间距（方块）。越小越连续，粒子数越多。 */
    private static final double TRAIL_SPACING = 0.35;
    /** 单颗球单 tick 内单段拖尾的撒点上限，防止极端速度下粒子数失控。 */
    private static final int MAX_TRAIL_PER_SEGMENT = 48;

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
        if (!enabled || params == null) {
            return;
        }
        List<Ball> landed = new ArrayList<>();
        int trail = params.trailCount();
        boolean force = params.force();
        for (Ball ball : balls) {
            ball.tick(world, trail, force);
            if (ball.consumeLandedFlag()) {
                landed.add(ball);
            }
        }
        for (Ball ball : landed) {
            ClientDisplayManager.reportBallLanded(ball.index);
        }
    }

    private static final class Ball {

        /** 多球区分用的高亮色板（各球取不同颜色，亮色 + 叠加混合，短距离内即可分辨）。 */
        private static final int[] PALETTE = {
                0xFFF2E0, // 暖白（单球默认，最“彗星”）
                0xFFD77A, // 琥珀
                0x7ADEFF, // 天蓝
                0xFF9AE0, // 粉红
                0x9AFF8A, // 薄荷
                0xB69AFF, // 紫罗兰
                0xFFFF9A, // 柠檬
                0x66FFE6  // 青玉
        };

        final int index;
        final int color;
        Vec3d pos;
        boolean idle = true;
        private Vec3d lastPos;
        private Vec3d start;
        private Vec3d target;
        private Vec3d restPos;
        private int jumpTicks;
        private int jumpElapsed;
        private float height = 3f;
        private HorizontalCurve curve = HorizontalCurve.ARC;
        private boolean landedFlag;
        private boolean jumpReported;
        private Vec3d pendingTarget;
        private float pendingHeight;
        private HorizontalCurve pendingCurve;

        Ball(int index, Vec3d initialPos) {
            this.index = index;
            this.color = PALETTE[index % PALETTE.length];
            this.pos = initialPos;
            this.lastPos = initialPos;
            this.restPos = initialPos;
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
            this.curve = curve;
            this.height = maxHeight;
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

        void tick(ClientWorld world, int trailCount, boolean force) {
            if (idle) {
                spawnRestGlow(world, force);
                return;
            }
            lastPos = pos;
            jumpElapsed++;
            float t = Math.min(1f, (float) jumpElapsed / jumpTicks);
            pos = computePosition(t);
            spawnParticles(world, trailCount, force);
            if (t >= 1f) {
                pos = target;
                idle = true;
                restPos = target;
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

        private void spawnParticles(ClientWorld world, int trailCount, boolean force) {
            int age = Math.max(4, Math.min(256, trailCount));
            double dx = pos.x - lastPos.x;
            double dy = pos.y - lastPos.y;
            double dz = pos.z - lastPos.z;
            double segLen = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // 拖尾：沿本 tick 走过的路径段密集采样成连续彗尾，星点静止、自然老化和淡出。
            if (segLen > 0.01) {
                int n = Math.max(1, (int) Math.min(MAX_TRAIL_PER_SEGMENT, Math.ceil(segLen / TRAIL_SPACING)));
                for (int i = 1; i <= n; i++) {
                    double t = (double) i / (n + 1);
                    double px = lastPos.x + dx * t + (world.random.nextFloat() - 0.5f) * 0.10;
                    double py = lastPos.y + dy * t + (world.random.nextFloat() - 0.5f) * 0.10;
                    double pz = lastPos.z + dz * t + (world.random.nextFloat() - 0.5f) * 0.10;
                    ColoredEndRodParticle.spawnMoving(world, px, py, pz, 0, 0, 0, color, 0.9f, age, force);
                }
            }

            // 球头：圆光核，带本 tick 位移作为速度（渲染插值 → 逐帧平滑滑动）。
            ColoredEndRodParticle.spawnHead(world, pos.x, pos.y, pos.z, dx, dy, dz, color, 3.2f, 2, force);
            // 头后两层柔和补光，放大“球”的观感。
            ColoredEndRodParticle.spawnHead(world,
                    pos.x - dx * 0.2, pos.y - dy * 0.2, pos.z - dz * 0.2,
                    dx * 0.8, dy * 0.8, dz * 0.8, color, 1.7f, 3, force);
            ColoredEndRodParticle.spawnMoving(world, pos.x, pos.y, pos.z, dx, dy, dz, color, 1.0f, 3, force);
        }

        private void spawnRestGlow(ClientWorld world, boolean force) {
            if (restPos == null) {
                return;
            }
            // 隔帧撒 2 个细小静置光点，成本极低，空闲时也能看见球的位置。
            if (((world.getTime() + index) & 1L) != 0L) {
                return;
            }
            for (int k = 0; k < 2; k++) {
                double ox = (world.random.nextFloat() - 0.5f) * 0.6;
                double oz = (world.random.nextFloat() - 0.5f) * 0.6;
                double oy = world.random.nextFloat() * 0.3;
                ColoredEndRodParticle.spawnStatic(world,
                        restPos.x + ox, restPos.y + oy, restPos.z + oz,
                        color, 0.5f, 10, force);
            }
        }

        private Vec3d computePosition(float t) {
            double baseX = lerp(start.x, target.x, t);
            double baseZ = lerp(start.z, target.z, t);

            double dx = target.x - start.x;
            double dz = target.z - start.z;
            double len = Math.hypot(dx, dz);
            if (len > 1e-4 && curve != HorizontalCurve.LINE) {
                double nx = -dz / len;
                double nz = dx / len;
                double amp = Math.max(1.0, Math.min(6.0, len * 0.5));
                double offset;
                if (curve == HorizontalCurve.ARC) {
                    offset = amp * (1.0 - Math.cos(2 * Math.PI * t)) / 2.0;
                } else {
                    offset = amp * Math.sin(2 * Math.PI * t);
                }
                baseX += nx * offset;
                baseZ += nz * offset;
            }

            double y0 = start.y;
            double y1 = target.y;
            // 抛物线最高点 = 配置的最大跳动高度，竖直为固定样式。
            double y = lerp(y0, y1, t) + 4.0 * height * t * (1.0 - t);
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