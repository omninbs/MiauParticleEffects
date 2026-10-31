package org.miau.particleeffects.client.display;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.AnimationToken;
import org.miau.particleeffects.animation.AnimCategory;
import org.miau.particleeffects.client.ClientClock;
import org.miau.particleeffects.client.particle.ColoredEndRodParticle;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.model.ColorMode;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.RotationSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 附加特效实例：空心正方体 / 立体三角形(四面体) / 宇宙爆炸 / 水波。
 * 全部使用末地烛(end_rod)粒子生成；支持旋转（rotate）+ 欧拉角倾斜（towards）
 * + scale 动画 + 淡入淡出 + move 位移，以及纯色/渐变色/彩虹颜色/彩虹渐变四种颜色模式。
 */
public final class EffectInstance extends DisplayInstance {

    private static final int MAX_EDGE_POINTS = 2000;

    private final EffectDisplayParams params;
    private final List<Vec3> basePoints;
    private final List<Vector3f> explosionDirs;
    private final float[] explosionSpeeds;
    private final float minLocalX;
    private final float maxLocalX;
    private final float hueOffset;
    /** 上一 tick 的原点与缩放，用于求出每个点在本 tick 内的真实位移（含旋转/缩放）。 */
    private Vec3 lastTickOrigin = null;
    private float lastTickScale = 1f;
    private Vec3 prevTickOrigin = null;
    private float prevTickScale = 1f;
    private boolean explosionSpawned = false;

    public EffectInstance(EffectDisplayParams params) {
        super(params.pos(), params.orientation(),
                params.durationTicks(), params.enterTicks(), params.exitTicks(), params.delayTicks(),
                params.curve(), params.entry(), params.exit(),
                params.fade(), params.move(), params.id());
        this.params = params;
        switch (params.type()) {
            case CUBE -> this.basePoints = buildCube(params.size());
            case TETRA -> this.basePoints = buildTetra(params.size());
            default -> this.basePoints = List.of();
        }
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (Vec3 p : basePoints) {
            if ((float) p.x < lo) {
                lo = (float) p.x;
            }
            if ((float) p.x > hi) {
                hi = (float) p.x;
            }
        }
        this.minLocalX = lo;
        this.maxLocalX = hi;

        int explosionCount = 260;
        this.explosionDirs = new ArrayList<>(explosionCount);
        this.explosionSpeeds = new float[explosionCount];
        Random random = new Random();
        for (int i = 0; i < explosionCount; i++) {
            Vector3f dir = randomUnit(random);
            explosionDirs.add(dir);
            explosionSpeeds[i] = 0.55f + random.nextFloat() * 0.45f;
        }
        this.hueOffset = (float) Math.random();
    }

    private static List<Vec3> buildCube(float size) {
        float h = size / 2f;
        Vec3[] corners = {
                new Vec3(-h, -h, -h), new Vec3(h, -h, -h),
                new Vec3(h, -h, h), new Vec3(-h, -h, h),
                new Vec3(-h, h, -h), new Vec3(h, h, -h),
                new Vec3(h, h, h), new Vec3(-h, h, h)
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        List<Vec3> points = new ArrayList<>();
        for (int[] edge : edges) {
            sampleEdge(corners[edge[0]], corners[edge[1]], 0.15, points);
        }
        return points;
    }

    private static List<Vec3> buildTetra(float size) {
        float edgeLength = (float) (2.0 * Math.sqrt(2.0));
        float s = size / edgeLength;
        Vec3[] vertices = {
                new Vec3(s, s, s),
                new Vec3(s, -s, -s),
                new Vec3(-s, s, -s),
                new Vec3(-s, -s, s)
        };
        int[][] edges = {
                {0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}
        };
        List<Vec3> points = new ArrayList<>();
        for (int[] edge : edges) {
            sampleEdge(vertices[edge[0]], vertices[edge[1]], 0.15, points);
        }
        return points;
    }

    private static void sampleEdge(Vec3 a, Vec3 b, double spacing, List<Vec3> out) {
        Vec3 delta = b.subtract(a);
        double len = delta.length();
        int count = (int) Math.ceil(len / spacing);
        if (count < 1) {
            count = 1;
        }
        if (count > 40) {
            count = 40;
        }
        if (out.size() + count > MAX_EDGE_POINTS) {
            count = Math.max(1, MAX_EDGE_POINTS - out.size());
        }
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            out.add(a.add(delta.scale(t)));
        }
    }

    private static Vector3f randomUnit(Random random) {
        float yaw = random.nextFloat() * 2f * (float) Math.PI;
        float pitch = (float) Math.acos(2f * random.nextFloat() - 1f) - (float) Math.PI / 2f;
        float cosP = (float) Math.cos(pitch);
        return new Vector3f(
                cosP * (float) Math.cos(yaw),
                (float) Math.sin(pitch),
                cosP * (float) Math.sin(yaw));
    }

    @Override
    public boolean frameDriven() {
        return true;
    }

    @Override
    public void spawnParticles(ClientLevel world, float partialTick) {
        if (phase == Phase.DELETED || phase == Phase.PENDING) {
            return;
        }
        this.framePartial = partialTick;
        float progress = easedProgress(partialTick);
        float fadeAlpha = computeFadeAlpha();
        Vec3 moveOffset = computeMoveOffset(tickCounter, partialTick);

        AnimationSet activeAnim = phase == Phase.ENTER ? entryAnim
                : phase == Phase.EXIT ? exitAnim
                : AnimationSet.EMPTY;

        float scaleFactor = 1f;
        if (phase == Phase.ENTER || phase == Phase.EXIT) {
            var tokenOpt = activeAnim.of(AnimCategory.SCALE);
            if (tokenOpt.isPresent()) {
                scaleFactor = computeScaleFactor(tokenOpt.get(), progress);
            }
        }

        Vec3 origin = pos.add(moveOffset);
        // 只在 tick 边界更新基准：粒子速度以 tick 为单位，用帧间位移会让旧粒子追不上新位置。
        if (tickAdvanced) {
            prevTickOrigin = lastTickOrigin;
            prevTickScale = lastTickScale;
            lastTickOrigin = origin;
            lastTickScale = scaleFactor;
            tickAdvanced = false;
        }

        switch (params.type()) {
            case CUBE, TETRA -> spawnWireframe(world, origin, scaleFactor, fadeAlpha);
            case EXPLOSION -> spawnExplosion(world, origin);
            case WAVE -> spawnWave(world, origin);
        }
    }

    // ------------------------------------------------------------------ color modes

    private int resolveColor(float gradientT) {
        return switch (params.colorMode()) {
            case GRADIENT -> ColorCodec.sampleGradient(params.gradientColors(), gradientT);
            case RAINBOW_GRADIENT -> ColorCodec.hsvToRgb(
                    gradientT + ColorCodec.rainbowPhase(ClientClock.ticks()));
            default -> params.colorArgb() & 0xFFFFFF;
        };
    }

    private boolean isRainbowColor() {
        return params.colorMode() == ColorMode.RAINBOW_COLOR;
    }

    private void spawnParticle(ClientLevel world, double x, double y, double z,
                               double vx, double vy, double vz, float gradientT, float scale, int maxAge) {
        if (isRainbowColor()) {
            ColoredEndRodParticle.spawnRainbow(world, x, y, z, vx, vy, vz, scale, maxAge, hueOffset, false,
                    params.force());
        } else {
            int color = resolveColor(gradientT);
            if (vx * vx + vy * vy + vz * vz > 1e-8) {
                ColoredEndRodParticle.spawnMoving(world, x, y, z, vx, vy, vz, color, scale, maxAge,
                        params.force());
            } else {
                ColoredEndRodParticle.spawnStatic(world, x, y, z, color, scale, maxAge, params.force());
            }
        }
    }

    private float wireframeT(float localX) {
        if (maxLocalX <= minLocalX) {
            return 0f;
        }
        return (localX - minLocalX) / (maxLocalX - minLocalX);
    }

    // ------------------------------------------------------------------ wireframe

    private void spawnWireframe(ClientLevel world, Vec3 origin, float scaleFactor, float fadeAlpha) {
        if (fadeAlpha < 0.05f) {
            return;
        }
        float spinTime = cumulativeSpanTicks() + framePartial;
        Quaternionf rotation = computeOrientation().mul(computeSpinAt(spinTime), new Quaternionf());
        // 上一 tick 的旋转角：同一局部点两次世界位置的差，就是它在本 tick 内的真实位移。
        Quaternionf prevRotation = computeOrientation().mul(computeSpinAt(spinTime - 1f), new Quaternionf());
        boolean hasPrev = prevTickOrigin != null;
        for (Vec3 p : basePoints) {
            Vector3f v = new Vector3f((float) p.x, (float) p.y, (float) p.z).rotate(rotation);
            Vec3 worldPos = origin.add(
                    v.x * scaleFactor,
                    v.y * scaleFactor,
                    v.z * scaleFactor);
            double vx = 0;
            double vy = 0;
            double vz = 0;
            if (hasPrev) {
                Vector3f prev = new Vector3f((float) p.x, (float) p.y, (float) p.z).rotate(prevRotation);
                Vec3 prevPos = prevTickOrigin.add(
                        prev.x * prevTickScale,
                        prev.y * prevTickScale,
                        prev.z * prevTickScale);
                vx = (worldPos.x - prevPos.x) * PARTICLE_VELOCITY_SCALE;
                vy = (worldPos.y - prevPos.y) * PARTICLE_VELOCITY_SCALE;
                vz = (worldPos.z - prevPos.z) * PARTICLE_VELOCITY_SCALE;
            }
            spawnParticle(world, worldPos.x, worldPos.y, worldPos.z,
                    vx, vy, vz,
                    wireframeT((float) p.x), 1f, 2);
        }
    }

    // ------------------------------------------------------------------ explosion

    private void spawnExplosion(ClientLevel world, Vec3 origin) {
        // 一次性迸发：在进入显示期（入场动画结束后）触发，粒子飞行距离恰好等于
        // 目标半径（速度补偿粒子自身的阻力衰减），生命覆盖显示期 + 出场期。
        if (phase != Phase.DISPLAY || explosionSpawned) {
            return;
        }
        explosionSpawned = true;
        int span = Math.max(1, durationTicks + exitTicks);
        float m = 0.91f;
        double denom = 1 - Math.pow(m, span);
        double perTick = denom > 1e-6 ? (1 - m) / denom : (1 - m);
        int count = explosionDirs.size();
        for (int i = 0; i < count; i++) {
            Vector3f dir = explosionDirs.get(i);
            double speed = params.size() * explosionSpeeds[i];
            double v0 = speed * perTick;
            spawnParticle(world, origin.x, origin.y, origin.z,
                    dir.x * v0, dir.y * v0, dir.z * v0,
                    (float) i / count, 1f, span);
        }
    }

    // ------------------------------------------------------------------ wave

    /** 水波固定颜色：水色（淡蓝）。 */
    private static final int WAVE_COLOR = 0x3F77E6;

    /**
     * 物理化水波：在水平面（XZ 平面）扩散的 2D 空心圆。
     *
     * <p>物理依据：</p>
     * <ul>
     *   <li>2D 表面波能量守恒：能量分布在周长 2πr 上，振幅 ∝ 1/√r</li>
     *   <li>粘性耗散：振幅随时间按 exp(-k·t) 衰减</li>
     *   <li>波纹结构：主波在前，尾随若干个次级波纹（间距 = 波长）</li>
     *   <li>边界淡出：半径接近最大值时振幅归零</li>
     * </ul>
     * <p>振幅最终映射到粒子大小，让水波呈现「中心强、边缘弱」的自然外观。</p>
     */
    private void spawnWave(ClientLevel world, Vec3 origin) {
        float speedPerTick = Math.max(0.005f, params.waveSpeed() / 20f);
        float maxRadius = params.size();
        float elapsed = cumulativeSpanTicks() + framePartial;

        // 波长：决定同心圆波纹间距，取最大半径的 18%
        float wavelength = Math.max(0.5f, maxRadius * 0.18f);
        // 波纹数量：主波 + 2 个尾随波纹
        int waveCount = 3;
        // 粘性耗散系数
        float decayK = 0.012f;
        // EXIT 阶段快速淡出
        float exitFade = phase == Phase.EXIT && exitTicks > 0
                ? Math.max(0f, 1f - (float) tickCounter / exitTicks)
                : 1f;
        if (exitFade <= 0f) {
            return;
        }

        for (int w = 0; w < waveCount; w++) {
            // 第 w 个波纹的半径：主波(w=0)在前，尾随波纹(w>0)滞后一个波长
            float r = speedPerTick * elapsed - w * wavelength;
            if (r <= 0.05f || r > maxRadius) {
                continue;
            }

            // 振幅衰减：1/√r（2D 表面波能量守恒）
            float amp = (float) (1.0 / Math.sqrt(r + 0.1));
            // 粘性耗散
            amp *= (float) Math.exp(-elapsed * decayK);
            // 尾随波纹本身更弱
            amp *= (1f - w * 0.25f);

            // 边界淡出：半径接近最大值时振幅归零
            float rNorm = r / maxRadius;
            float fadeOut = rNorm > 0.8f ? Math.max(0f, (1f - rNorm) / 0.2f) : 1f;
            // 入场淡入：最初 2 tick 振幅从 0 升到 1，避免突兀
            float fadeIn = elapsed < 2f ? elapsed / 2f : 1f;

            float effective = amp * fadeOut * fadeIn * exitFade;
            if (effective < 0.05f) {
                continue;
            }

            // 振幅映射到粒子大小（0.3~1.4）
            float scale = 0.3f + effective * 1.1f;

            // 生成圆环：周长越长粒子越密，但封顶 300
            float circumference = (float) (2 * Math.PI * r);
            int count = Math.max(16, (int) (circumference / 0.35));
            if (count > 300) {
                count = 300;
            }

            for (int i = 0; i < count; i++) {
                float angle = (float) (2 * Math.PI * i / count);
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                double px = origin.x + cos * r;
                double py = origin.y;
                double pz = origin.z + sin * r;
                ColoredEndRodParticle.spawnStatic(world, px, py, pz, WAVE_COLOR, scale, 2, false);
            }
        }
    }

    private int cumulativeSpanTicks() {
        return switch (phase) {
            case ENTER -> tickCounter;
            case DISPLAY -> enterTicks + tickCounter;
            case EXIT -> enterTicks + durationTicks + tickCounter;
            default -> 0;
        };
    }

    private float computeScaleFactor(AnimationToken token, float progress) {
        boolean shrink = token.style().equals("shrink");
        float extreme = shrink ? 3.0f : 0.1f;
        if (phase == Phase.ENTER) {
            return lerp(extreme, 1f, progress);
        }
        return lerp(1f, extreme, progress);
    }

    private Quaternionf computeOrientation() {
        float yawRad = (float) Math.toRadians(orientation.yaw());
        float pitchRad = (float) Math.toRadians(orientation.pitch());
        float rollRad = (float) Math.toRadians(orientation.roll());
        return new Quaternionf().rotationYXZ(yawRad, pitchRad, rollRad);
    }

    /** 按绝对累计时间（tick）求旋转角，便于同时取「当前」与「上一 tick」的旋转。 */
    private Quaternionf computeSpinAt(float time) {
        RotationSpec rotation = params.rotation();
        if (rotation == null) {
            return new Quaternionf();
        }
        Vector3f axis = rotation.axis();
        float len = axis.length();
        if (len < 1e-6f) {
            return new Quaternionf();
        }
        // 使用跨阶段单调递增的累计时间，避免 ENTER→DISPLAY→EXIT 时 tickCounter 归零导致旋转跳变。
        float angle = (float) Math.toRadians(rotation.degreesPerTick() * time);
        return new Quaternionf().rotationAxis(angle, axis.x / len, axis.y / len, axis.z / len);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
