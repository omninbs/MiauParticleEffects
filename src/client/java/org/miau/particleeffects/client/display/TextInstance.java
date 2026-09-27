package org.miau.particleeffects.client.display;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.AnimationToken;
import org.miau.particleeffects.animation.AnimCategory;
import org.miau.particleeffects.client.particle.ColoredEndRodParticle;
import org.miau.particleeffects.client.raster.FontRasterizer;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.model.ColorMode;
import org.miau.particleeffects.model.TextDisplayParams;

import java.util.List;

/**
 * 文字显示实例：用末地烛(end_rod)粒子当作像素来显示文字。
 *
 * 入场/出场动画（同类互斥，跨类并行）：
 *   scale: shrink（过大→正常）/ enlarge（极小→正常）
 *   slide: up/down/left/right
 *   spacing: converge（间距大→正常）/ disperse（间距小→正常）
 *   clarity: clear（模糊→清晰）/ blur（超清→正常）
 * 附加：fade in / fade out；显示期：move 位移。
 */
public final class TextInstance extends DisplayInstance {

    private final TextDisplayParams params;
    private final List<FontRasterizer.ParticlePixel> pixels;
    private final float minLocalX;
    private final float maxLocalX;
    private final float hueOffset;
    private Vec3d lastBaseOffset = null;

    public TextInstance(TextDisplayParams params) {
        super(params.pos(), params.orientation(),
                params.durationTicks(), params.enterTicks(), params.exitTicks(), params.delayTicks(),
                params.curve(), params.entry(), params.exit(),
                params.fade(), params.move(), params.id());
        this.params = params;
        FontRasterizer.RasterResult result =
                FontRasterizer.rasterize(params.text(), params.colorArgb(), params.density(), params.scale());
        this.pixels = result.pixels();
        float lo = Float.POSITIVE_INFINITY;
        float hi = Float.NEGATIVE_INFINITY;
        for (FontRasterizer.ParticlePixel px : pixels) {
            if (px.localX() < lo) lo = px.localX();
            if (px.localX() > hi) hi = px.localX();
        }
        this.minLocalX = lo;
        this.maxLocalX = hi;
        this.hueOffset = (float) Math.random();
    }

    @Override
    public void spawnParticles(ClientWorld world) {
        if (phase == Phase.DELETED || phase == Phase.PENDING) {
            return;
        }
        if (pixels.isEmpty()) {
            return;
        }

        float progress = easedProgress();
        float fadeAlpha = computeFadeAlpha();
        Vec3d moveOffset = computeMoveOffset(tickCounter, 0f);

        AnimationSet activeAnim = phase == Phase.ENTER ? entryAnim
                : phase == Phase.EXIT ? exitAnim
                : AnimationSet.EMPTY;

        float scaleFactor = 1f;
        Vec3d slideOffset = Vec3d.ZERO;
        float spacingFactor = 1f;
        float clarityFactor = 1f;

        if (phase == Phase.ENTER || phase == Phase.EXIT) {
            if (activeAnim.of(AnimCategory.SCALE).isPresent()) {
                scaleFactor = computeScaleFactor(activeAnim.of(AnimCategory.SCALE).get(), progress);
            }
            if (activeAnim.of(AnimCategory.SLIDE).isPresent()) {
                slideOffset = computeSlideOffset(activeAnim.of(AnimCategory.SLIDE).get(), progress);
            }
            if (activeAnim.of(AnimCategory.SPACING).isPresent()) {
                spacingFactor = computeSpacingFactor(activeAnim.of(AnimCategory.SPACING).get(), progress);
            }
            if (activeAnim.of(AnimCategory.CLARITY).isPresent()) {
                clarityFactor = computeClarityFactor(progress);
            }
        }

        Vec3d baseOffset = pos.add(moveOffset).add(slideOffset);
        int stride = Math.max(1, Math.round(clarityFactor));
        ColorMode colorMode = params.colorMode();
        long worldTime = world.getTime();

        // 位移速度：让本 tick 重生成的像素粒子沿位移方向滑行，使旧层与新层重合，消除残影。
        Vec3d delta = Vec3d.ZERO;
        if (lastBaseOffset != null) {
            delta = baseOffset.subtract(lastBaseOffset);
        }
        lastBaseOffset = baseOffset;

        for (int i = 0; i < pixels.size(); i += stride) {
            FontRasterizer.ParticlePixel px = pixels.get(i);
            if (fadeAlpha < 0.999f && world.random.nextFloat() >= fadeAlpha) {
                continue;
            }
            int argb = px.colorArgb();
            float pixelAlpha = ((argb >> 24) & 0xFF) / 255f;
            float sizeFactor = 0.35f + 0.65f * pixelAlpha;
            float localX = px.localX() * spacingFactor * scaleFactor;
            float localY = px.localY() * scaleFactor;
            Vec3d local = transformLocalToWorld(localX, localY);
            Vec3d worldPos = baseOffset.add(local);
            if (colorMode == ColorMode.RAINBOW_COLOR) {
                ColoredEndRodParticle.spawnRainbow(world, worldPos.x, worldPos.y, worldPos.z,
                        delta.x, delta.y, delta.z, sizeFactor, 2, hueOffset, false, params.force());
            } else {
                int colorRgb = resolvePixelColor(px, colorMode, worldTime);
                ColoredEndRodParticle.spawnMoving(world, worldPos.x, worldPos.y, worldPos.z,
                        delta.x, delta.y, delta.z, colorRgb, sizeFactor, 2, params.force());
            }
        }
    }

    private int resolvePixelColor(FontRasterizer.ParticlePixel px, ColorMode mode, long worldTime) {
        return switch (mode) {
            case GRADIENT -> ColorCodec.sampleGradient(params.gradientColors(), gradientT(px.localX()));
            case RAINBOW_GRADIENT -> ColorCodec.hsvToRgb(
                    gradientT(px.localX()) + ColorCodec.rainbowPhase(worldTime));
            default -> px.colorArgb() & 0xFFFFFF;
        };
    }

    private float gradientT(float localX) {
        if (maxLocalX <= minLocalX) {
            return 0f;
        }
        return (localX - minLocalX) / (maxLocalX - minLocalX);
    }

    private float computeScaleFactor(AnimationToken token, float progress) {
        boolean shrink = token.style().equals("shrink");
        float extreme = shrink ? 3.0f : 0.1f;
        if (phase == Phase.ENTER) {
            return lerp(extreme, 1f, progress);
        }
        return lerp(1f, extreme, progress);
    }

    private Vec3d computeSlideOffset(AnimationToken token, float progress) {
        String dir = token.style();
        float distance = params.scale() * 5f;
        float offset;
        Vec3d dirVec;
        if (phase == Phase.ENTER) {
            offset = lerp(distance, 0f, progress);
        } else {
            offset = lerp(0f, distance, progress);
        }
        dirVec = switch (dir) {
            case "up" -> new Vec3d(0, 1, 0);
            case "down" -> new Vec3d(0, -1, 0);
            case "left" -> transformLocalToWorld(-1f, 0f);
            case "right" -> transformLocalToWorld(1f, 0f);
            default -> Vec3d.ZERO;
        };
        return dirVec.multiply(offset);
    }

    private float computeSpacingFactor(AnimationToken token, float progress) {
        boolean converge = token.style().equals("converge");
        float extreme = params.spread();
        if (phase == Phase.ENTER) {
            return converge ? lerp(extreme, 1f, progress) : lerp(1f / extreme, 1f, progress);
        }
        return converge ? lerp(1f, extreme, progress) : lerp(1f, 1f / extreme, progress);
    }

    private float computeClarityFactor(float progress) {
        // clarity 只有“清晰(间距1) ↔ 模糊(间距2.5)”一个维度；clear/blur 是同一效果的两个别名，
        // 语义统一为：入场由糊到清晰（聚焦），出场由清晰到糊（散焦淡出）。
        float extreme = 2.5f;
        if (phase == Phase.ENTER) {
            return lerp(extreme, 1f, progress);
        }
        return lerp(1f, extreme, progress);
    }

    private Vec3d transformLocalToWorld(float localX, float localY) {
        float yawRad = (float) Math.toRadians(orientation.yaw());
        float pitchRad = (float) Math.toRadians(orientation.pitch());
        float rollRad = (float) Math.toRadians(orientation.roll());

        float x = localX;
        float y = localY;

        float cosR = (float) Math.cos(rollRad);
        float sinR = (float) Math.sin(rollRad);
        float x1 = x * cosR - y * sinR;
        float y1 = x * sinR + y * cosR;

        float cosP = (float) Math.cos(pitchRad);
        float sinP = (float) Math.sin(pitchRad);
        float y2 = y1 * cosP;
        float z2 = y1 * sinP;

        float cosY = (float) Math.cos(yawRad);
        float sinY = (float) Math.sin(yawRad);
        float x3 = x1 * cosY + z2 * sinY;
        float z3 = -x1 * sinY + z2 * cosY;

        return new Vec3d(x3, y2, z3);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
