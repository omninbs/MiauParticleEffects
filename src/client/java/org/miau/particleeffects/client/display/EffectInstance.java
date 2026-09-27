package org.miau.particleeffects.client.display;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.AnimationToken;
import org.miau.particleeffects.animation.AnimCategory;
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
    private final List<Vec3d> basePoints;
    private final List<Vector3f> explosionDirs;
    private final float[] explosionSpeeds;
    private final float minLocalX;
    private final float maxLocalX;
    private final float hueOffset;
    private Vec3d lastOrigin = null;
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
        for (Vec3d p : basePoints) {
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

    private static List<Vec3d> buildCube(float size) {
        float h = size / 2f;
        Vec3d[] corners = {
                new Vec3d(-h, -h, -h), new Vec3d(h, -h, -h),
                new Vec3d(h, -h, h), new Vec3d(-h, -h, h),
                new Vec3d(-h, h, -h), new Vec3d(h, h, -h),
                new Vec3d(h, h, h), new Vec3d(-h, h, h)
        };
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        List<Vec3d> points = new ArrayList<>();
        for (int[] edge : edges) {
            sampleEdge(corners[edge[0]], corners[edge[1]], 0.15, points);
        }
        return points;
    }

    private static List<Vec3d> buildTetra(float size) {
        float edgeLength = (float) (2.0 * Math.sqrt(2.0));
        float s = size / edgeLength;
        Vec3d[] vertices = {
                new Vec3d(s, s, s),
                new Vec3d(s, -s, -s),
                new Vec3d(-s, s, -s),
                new Vec3d(-s, -s, s)
        };
        int[][] edges = {
                {0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}
        };
        List<Vec3d> points = new ArrayList<>();
        for (int[] edge : edges) {
            sampleEdge(vertices[edge[0]], vertices[edge[1]], 0.15, points);
        }
        return points;
    }

    private static void sampleEdge(Vec3d a, Vec3d b, double spacing, List<Vec3d> out) {
        Vec3d delta = b.subtract(a);
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
            out.add(a.add(delta.multiply(t)));
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
    public void spawnParticles(ClientWorld world) {
        if (phase == Phase.DELETED || phase == Phase.PENDING) {
            return;
        }
        float progress = easedProgress();
        float fadeAlpha = computeFadeAlpha();
        Vec3d moveOffset = computeMoveOffset(tickCounter, 0f);

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

        Vec3d origin = pos.add(moveOffset);
        Vec3d originDelta = Vec3d.ZERO;
        if (lastOrigin != null) {
            originDelta = origin.subtract(lastOrigin);
        }
        lastOrigin = origin;

        switch (params.type()) {
            case CUBE, TETRA -> spawnWireframe(world, origin, originDelta, scaleFactor, fadeAlpha);
            case EXPLOSION -> spawnExplosion(world, origin);
            case WAVE -> spawnWave(world, origin, originDelta, scaleFactor, fadeAlpha);
        }
    }

    // ------------------------------------------------------------------ color modes

    private int resolveColor(ClientWorld world, float gradientT) {
        return switch (params.colorMode()) {
            case GRADIENT -> ColorCodec.sampleGradient(params.gradientColors(), gradientT);
            case RAINBOW_GRADIENT -> ColorCodec.hsvToRgb(
                    gradientT + ColorCodec.rainbowPhase(world.getTime()));
            default -> params.colorArgb() & 0xFFFFFF;
        };
    }

    private boolean isRainbowColor() {
        return params.colorMode() == ColorMode.RAINBOW_COLOR;
    }

    private void spawnParticle(ClientWorld world, double x, double y, double z,
                               double vx, double vy, double vz, float gradientT, float scale, int maxAge) {
        if (isRainbowColor()) {
            ColoredEndRodParticle.spawnRainbow(world, x, y, z, vx, vy, vz, scale, maxAge, hueOffset, false,
                    params.force());
        } else {
            int color = resolveColor(world, gradientT);
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

    private void spawnWireframe(ClientWorld world, Vec3d origin, Vec3d originDelta,
                                float scaleFactor, float fadeAlpha) {
        if (fadeAlpha < 0.05f) {
            return;
        }
        Quaternionf rotation = computeOrientation().mul(computeSpin(0f), new Quaternionf());
        for (Vec3d p : basePoints) {
            Vector3f v = new Vector3f((float) p.x, (float) p.y, (float) p.z).rotate(rotation);
            Vec3d worldPos = origin.add(
                    v.x * scaleFactor,
                    v.y * scaleFactor,
                    v.z * scaleFactor);
            spawnParticle(world, worldPos.x, worldPos.y, worldPos.z,
                    originDelta.x, originDelta.y, originDelta.z,
                    wireframeT((float) p.x), 1f, 2);
        }
    }

    // ------------------------------------------------------------------ explosion

    private void spawnExplosion(ClientWorld world, Vec3d origin) {
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

    private void spawnWave(ClientWorld world, Vec3d origin, Vec3d originDelta,
                           float scaleFactor, float fadeAlpha) {
        if (fadeAlpha < 0.05f) {
            return;
        }
        Quaternionf rotation = computeOrientation().mul(computeSpin(0f), new Quaternionf());
        // speed：展开速度（方块/秒），默认已由命令层按“2 秒展开到最大”折算。
        float speedPerTick = Math.max(0.005f, params.waveSpeed() / 20f);
        int expandTicks = (int) Math.ceil(params.size() / speedPerTick);
        if (expandTicks < 1) {
            expandTicks = 1;
        }
        int elapsed = cumulativeSpanTicks();
        float t = Math.min(1f, (float) elapsed / expandTicks);
        float radius = params.size() * t;
        float gradientT = Math.min(1f, radius / params.size());

        float circumference = (float) (2 * Math.PI * radius);
        int count = Math.max(16, (int) (circumference / 0.35));
        if (count > 300) {
            count = 300;
        }
        boolean expanding = t < 1f;
        for (int i = 0; i < count; i++) {
            float angle = (float) (2 * Math.PI * i / count);
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);
            Vector3f local = new Vector3f(cos * radius, 0f, sin * radius);
            Vector3f worldV = local.rotate(rotation);
            Vec3d worldPos = origin.add(
                    worldV.x * scaleFactor,
                    worldV.y * scaleFactor,
                    worldV.z * scaleFactor);
            double vx = originDelta.x;
            double vy = originDelta.y;
            double vz = originDelta.z;
            if (expanding) {
                // 沿径向向外速度：让粒子在帧间平滑外扩（渲染插值），避免环面一格一格跳。
                Vector3f radial = new Vector3f(cos, 0f, sin).rotate(rotation);
                vx += radial.x * speedPerTick;
                vy += radial.y * speedPerTick;
                vz += radial.z * speedPerTick;
            }
            spawnParticle(world, worldPos.x, worldPos.y, worldPos.z, vx, vy, vz, gradientT, 1f, 2);
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

    private Quaternionf computeSpin(float partialTick) {
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
        float angle = (float) Math.toRadians(rotation.degreesPerTick() * (cumulativeSpanTicks() + partialTick));
        return new Quaternionf().rotationAxis(angle, axis.x / len, axis.y / len, axis.z / len);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
