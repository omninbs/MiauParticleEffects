package org.miau.particleeffects.client.noteblock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.client.particle.ColoredEndRodParticle;
import org.miau.particleeffects.model.HorizontalCurve;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.util.WorldTime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 音符盒弹力球客户端模拟：接收服务端分配的目标，水平直线飞向目标，
 * 仅在 Y 轴做起伏（arc=抛物线 / sine=正弦 / line=不弹跳）。
 *
 * 轨道模型：一颗弹力球对应一条已选轨道（ballIndex == trackIndex），因此球永远不会串轨。
 *
 * 节奏同步：服务端会提前预测本轨“下一个被激活的音符盒”并附带提前量（提前 tick 数），
 * 客户端据此把本次跳跃时长设为该提前量，使球正好在下一个音符盒发声时落到其上。
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
 * 连击优化：服务端提前下发的下一跳先进本地待跳队列，落地瞬间立刻接上，消除两次跳跃之间的停顿。
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

    public void start(NoteBlockParams newParams, List<Vec3> anchors) {
        this.params = newParams;
        this.enabled = true;
        this.balls.clear();
        // 一颗弹力球对应一条轨道，初始停在该轨道的锚点上（无锚点时退回中心）。
        int count = !anchors.isEmpty() ? anchors.size() : newParams.ballCount();
        for (int i = 0; i < count; i++) {
            Vec3 home = i < anchors.size() ? anchors.get(i) : newParams.center();
            balls.add(new Ball(i, home));
        }
    }

    public void stop() {
        this.enabled = false;
        this.balls.clear();
        this.params = null;
    }

    public void activate(int ballIndex, Vec3 target, int note, int durationTicks) {
        if (!enabled || params == null || ballIndex < 0 || ballIndex >= balls.size()) {
            return;
        }
        balls.get(ballIndex).queueJump(target, params.maxJumpHeight(), params.curve(), durationTicks);
    }

    public void tick(ClientLevel world) {
        if (!enabled || params == null) {
            return;
        }
        int trail = params.trailCount();
        boolean force = params.force();
        for (Ball ball : balls) {
            ball.tick(world, trail, force);
        }
    }

    private static final class Ball {

        /** 单次跳跃的 tick 上限，防止长距离跳跃拖得过久。 */
        private static final int MAX_JUMP_TICKS = 60;
        /** 无预测时（歌曲第一遍）的兜底跳跃时长下限，尽量快速追上后续音符。 */
        private static final int MIN_FALLBACK_TICKS = 3;

        final int index;
        final int color;
        Vec3 pos;
        boolean idle = true;
        private Vec3 lastPos;
        private Vec3 start;
        private Vec3 target;
        private Vec3 restPos;
        private int jumpTicks;
        private int jumpElapsed;
        private float height = 3f;
        private HorizontalCurve curve = HorizontalCurve.ARC;
        private static final int MAX_PENDING_JUMPS = 8;
        private final Deque<PendingJump> pendingJumps = new ArrayDeque<>();

        private record PendingJump(Vec3 target, float height, HorizontalCurve curve, int duration) {
        }

        Ball(int index, Vec3 initialPos) {
            this.index = index;
            this.color = colorOf(index);
            this.pos = initialPos;
            this.lastPos = initialPos;
            this.restPos = initialPos;
        }

        /** 按黄金比例散开色相，让最多 32 颗弹力球颜色都尽量可分辨。 */
        private static int colorOf(int index) {
            float hue = (float) ((index * 0.61803398875) % 1.0);
            float sat = 0.45f;
            float val = 1.0f;
            int sector = (int) (hue * 6f);
            float f = hue * 6f - sector;
            float p = val * (1f - sat);
            float q = val * (1f - sat * f);
            float t = val * (1f - sat * (1f - f));
            float r;
            float g;
            float b;
            switch (sector % 6) {
                case 0 -> { r = val; g = t; b = p; }
                case 1 -> { r = q; g = val; b = p; }
                case 2 -> { r = p; g = val; b = t; }
                case 3 -> { r = p; g = q; b = val; }
                case 4 -> { r = t; g = p; b = val; }
                default -> { r = val; g = p; b = q; }
            }
            return ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
        }

        void queueJump(Vec3 newTarget, float maxHeight, HorizontalCurve curve, int durationTicks) {
            if (idle) {
                beginJump(newTarget, maxHeight, curve, durationTicks);
                return;
            }
            if (!pendingJumps.isEmpty()) {
                PendingJump last = pendingJumps.peekLast();
                if (last.target().equals(newTarget)) {
                    pendingJumps.pollLast();
                }
            }
            while (pendingJumps.size() >= MAX_PENDING_JUMPS) {
                pendingJumps.pollFirst();
            }
            pendingJumps.addLast(new PendingJump(newTarget, maxHeight, curve, durationTicks));
        }

        void beginJump(Vec3 newTarget, float maxHeight, HorizontalCurve curve, int durationTicks) {
            this.start = this.pos;
            this.target = newTarget;
            this.curve = curve;
            this.height = maxHeight;
            if (durationTicks > 0) {
                // 服务端预测的提前量：用“到下一音符盒激活还剩多少 tick”作为跳跃时长，
                // 让球正好在下一个音符盒发声时落上去，跟上歌曲节奏。
                this.jumpTicks = Math.max(2, Math.min(MAX_JUMP_TICKS, durationTicks));
            } else {
                double dist = Math.hypot(target.x - start.x, target.z - start.z);
                int ticks = (int) Math.ceil(dist / 2.0);
                this.jumpTicks = Math.max(MIN_FALLBACK_TICKS, Math.min(MAX_JUMP_TICKS, ticks));
            }
            this.jumpElapsed = 0;
            this.idle = false;
        }

        void tick(ClientLevel world, int trailCount, boolean force) {
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
                if (!pendingJumps.isEmpty()) {
                    PendingJump next = pendingJumps.pollFirst();
                    beginJump(next.target(), next.height(), next.curve(), next.duration());
                }
            }
        }

        private void spawnParticles(ClientLevel world, int trailCount, boolean force) {
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
                    double px = lastPos.x + dx * t + (world.getRandom().nextFloat() - 0.5f) * 0.10;
                    double py = lastPos.y + dy * t + (world.getRandom().nextFloat() - 0.5f) * 0.10;
                    double pz = lastPos.z + dz * t + (world.getRandom().nextFloat() - 0.5f) * 0.10;
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

        private void spawnRestGlow(ClientLevel world, boolean force) {
            if (restPos == null) {
                return;
            }
            // 隔帧撒 2 个细小静置光点，成本极低，空闲时也能看见球的位置。
            if (((WorldTime.ticks(world) + index) & 1L) != 0L) {
                return;
            }
            for (int k = 0; k < 2; k++) {
                double ox = (world.getRandom().nextFloat() - 0.5f) * 0.6;
                double oz = (world.getRandom().nextFloat() - 0.5f) * 0.6;
                double oy = world.getRandom().nextFloat() * 0.3;
                ColoredEndRodParticle.spawnStatic(world,
                        restPos.x + ox, restPos.y + oy, restPos.z + oz,
                        color, 0.5f, 10, force);
            }
        }

        private Vec3 computePosition(float t) {
            // 水平方向：直接直线插值到目标，不再有“固定偏向一侧”的横向弧线。
            double x = lerp(start.x, target.x, t);
            double z = lerp(start.z, target.z, t);
            double yBase = lerp(start.y, target.y, t);

            // 竖直方向：唯一做起伏的轴。arc = 抛物线（默认），sine = 正弦，line = 不弹跳。
            double y = switch (curve) {
                case LINE -> yBase;
                case ARC -> yBase + 4.0 * height * t * (1.0 - t);
                case SINE -> yBase + height * Math.sin(Math.PI * t);
            };
            if (y < Math.min(start.y, target.y)) {
                y = Math.min(start.y, target.y);
            }
            return new Vec3(x, y, z);
        }
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}