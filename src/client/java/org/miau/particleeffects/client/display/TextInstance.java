package org.miau.particleeffects.client.display;

import org.miau.particleeffects.client.ClientClock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.AnimationToken;
import org.miau.particleeffects.animation.AnimCategory;
import org.miau.particleeffects.animation.Easings;
import org.miau.particleeffects.client.particle.ColoredEndRodParticle;
import org.miau.particleeffects.client.raster.FontRasterizer;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.model.ColorMode;
import org.miau.particleeffects.model.TextDisplayParams;

import java.util.List;

/**
 * 文字显示实例：用末地烛(end_rod)粒子当作像素来显示文字。
 *
 * <p>入场/出场动画按类别互斥、跨类别可叠加：</p>
 * <ul>
 *   <li>{@code scale} shrink / enlarge —— 整体缩放</li>
 *   <li>{@code slide} up / down / left / right —— 整体平移</li>
 *   <li>{@code spacing} converge / disperse —— 字间距</li>
 *   <li>{@code clarity} clear / blur —— 清晰度（模糊 = 抖动 + 变暗 + 稀疏）</li>
 *   <li>{@code typewriter} left / right / center —— 逐字打字</li>
 *   <li>{@code wipe} left / right / up / down —— 沿边线擦除显现</li>
 *   <li>{@code flip} horizontal / vertical —— 翻转</li>
 *   <li>{@code scatter} gather / burst —— 随机聚拢 / 径向爆散</li>
 *   <li>{@code bounce} drop / rise —— 带弹跳的落位</li>
 *   <li>{@code zoom} in / out —— 推近拉远（粒子尺寸同步变化）</li>
 *   <li>{@code rotate} roll / yaw / pitch —— 旋转</li>
 *   <li>{@code drop} down / up / left / right —— 加速坠落</li>
 * </ul>
 * <p>显示期另有 {@code move} 位移与 {@code fade} 淡入淡出。</p>
 *
 * <p>性能：位图栅格化惰性化并带缓存；每个像素的朝向基向量在构造时算好，
 * 每 tick 只做标量乘加（不再逐像素求三角函数、也不再逐像素分配 Vec3）。</p>
 */
public final class TextInstance extends DisplayInstance {

    /** 像素数超过该阈值时，入场/出场动画的逐帧生成降频到约 30FPS，避免超大文字拖垮渲染线程。 */
    private static final int LARGE_TEXT_PIXELS = 2500;
    private static final long LARGE_TEXT_FRAME_INTERVAL = 33_000_000L;

    private final TextDisplayParams params;
    /** 朝向决定的局部基向量：world = base + ux * localX + uy * localY。 */
    private final double uxX;
    private final double uxY;
    private final double uxZ;
    private final double uyX;
    private final double uyY;
    private final double uyZ;
    private final float hueOffset;
    /** 粒子尺寸随像素密度放大，保证密度调大时笔画依然连成一笔，而不是散成一堆点。 */
    private final float densityScale;

    private List<FontRasterizer.ParticlePixel> pixels = List.of();
    private int charCount;
    private float minLocalX;
    private float maxLocalX;
    private float minLocalY;
    private float maxLocalY;
    /** 逐字打字的方向：0 从左往右、1 从右往左、2 从中间向两侧。 */
    private int typewriterMode;
    private long scatterSeed;
    private boolean rasterized;

    private long lastFrameNanos;
    private Vec3 lastTickBase = null;
    private Vec3 tickDelta = Vec3.ZERO;

    public TextInstance(TextDisplayParams params) {
        super(params.pos(), params.orientation(),
                params.durationTicks(), params.enterTicks(), params.exitTicks(), params.delayTicks(),
                params.curve(), params.entry(), params.exit(),
                params.fade(), params.move(), params.id());
        this.params = params;
        Vec3 ux = transformLocalToWorld(1f, 0f);
        Vec3 uy = transformLocalToWorld(0f, 1f);
        this.uxX = ux.x;
        this.uxY = ux.y;
        this.uxZ = ux.z;
        this.uyX = uy.x;
        this.uyY = uy.y;
        this.uyZ = uy.z;
        this.hueOffset = (float) Math.random();
        this.densityScale = (float) Math.min(3.0, Math.max(0.5, params.density() / 0.1));
    }

    /** 入场/出场阶段改由渲染帧驱动，动画更平滑；显示期仍按 tick 生成，控制粒子开销。 */
    @Override
    public boolean frameDriven() {
        return phase == Phase.ENTER || phase == Phase.EXIT;
    }

    /** 位图按需栅格化：延迟到真正要显示时才做，整条歌词链不会一次性栅格化所有句子。 */
    private void ensureRasterized() {
        if (rasterized) {
            return;
        }
        rasterized = true;
        FontRasterizer.RasterResult result = FontRasterizer.rasterize(
                params.text(), params.colorArgb(), params.density(), params.scale());
        this.pixels = result.pixels();
        this.charCount = result.charCount();
        if (pixels.isEmpty()) {
            return;
        }
        float loX = Float.POSITIVE_INFINITY;
        float hiX = Float.NEGATIVE_INFINITY;
        float loY = Float.POSITIVE_INFINITY;
        float hiY = Float.NEGATIVE_INFINITY;
        for (FontRasterizer.ParticlePixel px : pixels) {
            if (px.localX() < loX) loX = px.localX();
            if (px.localX() > hiX) hiX = px.localX();
            if (px.localY() < loY) loY = px.localY();
            if (px.localY() > hiY) hiY = px.localY();
        }
        this.minLocalX = loX;
        this.maxLocalX = hiX;
        this.minLocalY = loY;
        this.maxLocalY = hiY;
        this.scatterSeed = 0x9E3779B97F4A7C15L ^ (id() == null ? 0 : id().hashCode());
    }

    /** 逐字显现阈值：left 从左往右、right 从右往左、center 从中间向两侧。 */
    private float charThreshold(int charIndex) {
        if (charCount <= 0) {
            return 1f;
        }
        float center = (charCount - 1) / 2f;
        float half = Math.max(1f, (charCount + 1) / 2f);
        float t = switch (typewriterMode) {
            case 1 -> (charCount - charIndex) / (float) charCount;
            case 2 -> Math.abs(charIndex - center) / half;
            default -> (charIndex + 1) / (float) charCount;
        };
        return Math.min(1f, Math.max(0f, t));
    }

    /** 稳定的伪随机方向（每像素、每次播放一致，避免闪烁）。 */
    private double scatterDir(int index, int axis) {
        long h = scatterSeed + (long) index * 0x632BE59BD9B4E019L + axis * 0x9E3779B9L;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return ((h >>> 11) & 0x1FFFFF) / (double) 0x1FFFFF;
    }

    @Override
    public void spawnParticles(ClientLevel world, float partialTick) {
        if (phase == Phase.DELETED || phase == Phase.PENDING) {
            return;
        }        ensureRasterized();
        if (pixels.isEmpty()) {
            return;
        }
        boolean perFrame = partialTick > 0f;
        if (perFrame && pixels.size() > LARGE_TEXT_PIXELS) {
            long now = System.nanoTime();
            if (now - lastFrameNanos < LARGE_TEXT_FRAME_INTERVAL) {
                return;
            }
            lastFrameNanos = now;
        }
        this.framePartial = clampPartial(partialTick);

        float progress = easedProgress(partialTick);
        float fadeAlpha = computeFadeAlpha();
        if (fadeAlpha < 0.01f) {
            return;
        }
        Vec3 moveOffset = computeMoveOffset(tickCounter, partialTick);
        AnimationSet anim = phase == Phase.ENTER ? entryAnim
                : phase == Phase.EXIT ? exitAnim
                : AnimationSet.EMPTY;

        // ---- 整体变换 ----
        float scaleFactor = 1f;
        float particleScale = 1f;
        float spacingFactor = 1f;
        float flipX = 1f;
        float flipY = 1f;
        double cosR = 1;
        double sinR = 0;
        double extraX = 0;
        double extraY = 0;
        double extraZ = 0;

        AnimationToken scaleToken = anim.of(AnimCategory.SCALE).orElse(null);
        if (scaleToken != null) {
            scaleFactor *= computeScaleFactor(scaleToken, progress);
        }
        AnimationToken zoomToken = anim.of(AnimCategory.ZOOM).orElse(null);
        if (zoomToken != null) {
            float z = computeZoomFactor(zoomToken, progress);
            scaleFactor *= z;
            particleScale = 0.35f + 0.65f * z;
        }
        AnimationToken spacingToken = anim.of(AnimCategory.SPACING).orElse(null);
        if (spacingToken != null) {
            spacingFactor = computeSpacingFactor(spacingToken, progress);
        }
        AnimationToken flipToken = anim.of(AnimCategory.FLIP).orElse(null);
        if (flipToken != null) {
            float f = flipAmount(progress);
            if (flipToken.style().equals("vertical")) {
                flipY = f;
            } else {
                flipX = f;
            }
        }
        AnimationToken rotateToken = anim.of(AnimCategory.ROTATE).orElse(null);
        if (rotateToken != null) {
            switch (rotateToken.style()) {
                case "yaw" -> flipX *= flipAmount(progress);
                case "pitch" -> flipY *= flipAmount(progress);
                default -> {
                    double angle = Math.toRadians(90.0 * rotateAngle(progress));
                    cosR = Math.cos(angle);
                    sinR = Math.sin(angle);
                }
            }
        }
        AnimationToken slideToken = anim.of(AnimCategory.SLIDE).orElse(null);
        if (slideToken != null) {
            Vec3 slide = computeSlideOffset(slideToken, progress);
            extraX += slide.x;
            extraY += slide.y;
            extraZ += slide.z;
        }
        AnimationToken dropToken = anim.of(AnimCategory.DROP).orElse(null);
        if (dropToken != null) {
            Vec3 drop = computeDropOffset(dropToken, progress, false);
            extraX += drop.x;
            extraY += drop.y;
            extraZ += drop.z;
        }
        AnimationToken bounceToken = anim.of(AnimCategory.BOUNCE).orElse(null);
        if (bounceToken != null) {
            Vec3 bounce = computeDropOffset(bounceToken, progress, true);
            extraX += bounce.x;
            extraY += bounce.y;
            extraZ += bounce.z;
        }

        // ---- 逐像素参数 ----
        AnimationToken typewriterToken = anim.of(AnimCategory.TYPEWRITER).orElse(null);
        this.typewriterMode = typewriterToken == null ? 0 : switch (typewriterToken.style()) {
            case "right" -> 1;
            case "center" -> 2;
            default -> 0;
        };
        float reveal = revealAmount(anim, progress);
        int revealAxis = revealAxis(anim);
        float blur = blurAmount(anim, progress);

        double baseX = pos.x + moveOffset.x + extraX;
        double baseY = pos.y + moveOffset.y + extraY;
        double baseZ = pos.z + moveOffset.z + extraZ;

        if (tickAdvanced) {
            Vec3 base = new Vec3(baseX, baseY, baseZ);
            if (lastTickBase != null) {
                tickDelta = base.subtract(lastTickBase);
            }
            lastTickBase = base;
            tickAdvanced = false;
        }
        double speedX = tickDelta.x * PARTICLE_VELOCITY_SCALE;
        double speedY = tickDelta.y * PARTICLE_VELOCITY_SCALE;
        double speedZ = tickDelta.z * PARTICLE_VELOCITY_SCALE;

        ColorMode colorMode = params.colorMode();
        boolean rainbow = colorMode == ColorMode.RAINBOW_COLOR;
        boolean force = params.force();
        long worldTime = ClientClock.ticks();
        float invW = maxLocalX > minLocalX ? 1f / (maxLocalX - minLocalX) : 0f;
        float invH = maxLocalY > minLocalY ? 1f / (maxLocalY - minLocalY) : 0f;
        AnimationToken scatterToken = anim.of(AnimCategory.SCATTER).orElse(null);
        double scatterAmount = scatterToken == null ? 0 : scatterDistance(scatterToken, progress);
        boolean scatterRadial = scatterToken != null && scatterToken.style().equals("burst");
        int count = pixels.size();

        for (int i = 0; i < count; i++) {
            FontRasterizer.ParticlePixel px = pixels.get(i);
            // 逐字/擦除：先判断这一像素此刻是否该出现（省掉后面的全部计算）。
            if (reveal < 1f && !isRevealed(px, reveal, revealAxis, invW, invH)) {
                continue;
            }
            if (fadeAlpha < 0.999f && world.getRandom().nextFloat() >= fadeAlpha) {
                continue;
            }
            int argb = px.colorArgb();
            float pixelAlpha = ((argb >> 24) & 0xFF) / 255f;
            if (blur > 0f) {
                // 模糊：亮度下降并随机稀疏，形成「未聚焦」的观感（不改变整体位置，避免闪烁）。
                float keep = 1f - 0.55f * blur;
                if (world.getRandom().nextFloat() > keep) {
                    continue;
                }
                pixelAlpha *= 1f - 0.45f * blur;
            }
            float localX = px.localX() * spacingFactor * flipX;
            float localY = px.localY() * flipY;
            if (blur > 0f) {
                localX += (world.getRandom().nextFloat() - 0.5f) * blur * 0.25f;
                localY += (world.getRandom().nextFloat() - 0.5f) * blur * 0.25f;
            }
            float rx = (float) (localX * cosR - localY * sinR) * scaleFactor;
            float ry = (float) (localX * sinR + localY * cosR) * scaleFactor;

            double wx = baseX + uxX * rx + uyX * ry;
            double wy = baseY + uxY * rx + uyY * ry;
            double wz = baseZ + uxZ * rx + uyZ * ry;

            if (scatterAmount != 0) {
                double dx;
                double dy;
                double dz;
                if (scatterRadial) {
                    double len = Math.sqrt((double) rx * rx + (double) ry * ry);
                    if (len < 1e-4) {
                        dx = dy = dz = 0;
                    } else {
                        dx = (uxX * rx + uyX * ry) / len;
                        dy = (uxY * rx + uyY * ry) / len;
                        dz = (uxZ * rx + uyZ * ry) / len;
                    }
                } else {
                    dx = scatterDir(i, 0) * 2 - 1;
                    dy = scatterDir(i, 1) * 2 - 1;
                    dz = scatterDir(i, 2) * 2 - 1;
                }
                wx += dx * scatterAmount;
                wy += dy * scatterAmount;
                wz += dz * scatterAmount;
            }

            float sizeFactor = (0.35f + 0.65f * pixelAlpha) * particleScale * densityScale;
            if (rainbow) {
                ColoredEndRodParticle.spawnRainbow(world, wx, wy, wz,
                        speedX, speedY, speedZ, sizeFactor, 2, hueOffset, false, force);
            } else {
                int colorRgb = resolvePixelColor(px, colorMode, worldTime);
                ColoredEndRodParticle.spawnMoving(world, wx, wy, wz,
                        speedX, speedY, speedZ, colorRgb, sizeFactor, 2, force);
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

    // ------------------------------------------------------------------ 逐像素动画

    /** 入场：0 → 1 逐渐显现；出场：1 → 0 逐渐消失。返回 1 表示完全不需要裁剪。 */
    private float revealAmount(AnimationSet anim, float progress) {
        if (anim.of(AnimCategory.TYPEWRITER).isEmpty() && anim.of(AnimCategory.WIPE).isEmpty()) {
            return 1f;
        }
        return phase == Phase.ENTER ? progress : 1f - progress;
    }

    /**
     * 显现方向编码：0 从左/下开始，1 从右/上开始，2 逐字。
     */
    private int revealAxis(AnimationSet anim) {
        AnimationToken wipe = anim.of(AnimCategory.WIPE).orElse(null);
        if (wipe != null) {
            return switch (wipe.style()) {
                case "right" -> 1;
                case "up" -> 3;
                case "down" -> 4;
                default -> 0;
            };
        }
        return 2;
    }

    private boolean isRevealed(FontRasterizer.ParticlePixel px, float reveal,
                               int axis, float invW, float invH) {
        return switch (axis) {
            case 0 -> {
                float t = (px.localX() - minLocalX) * invW;
                yield t <= reveal;
            }
            case 1 -> {
                float t = 1f - (px.localX() - minLocalX) * invW;
                yield t <= reveal;
            }
            case 3 -> {
                float t = (px.localY() - minLocalY) * invH;
                yield t <= reveal;
            }
            case 4 -> {
                float t = 1f - (px.localY() - minLocalY) * invH;
                yield t <= reveal;
            }
            default -> {
                int charIndex = px.charIndex();
                yield charThreshold(charIndex) <= reveal;
            }
        };
    }

    private float blurAmount(AnimationSet anim, float progress) {
        if (anim.of(AnimCategory.CLARITY).isEmpty()) {
            return 0f;
        }
        // 入场：由糊到清；出场：由清到糊。
        return phase == Phase.ENTER ? 1f - progress : progress;
    }

    private float computeScaleFactor(AnimationToken token, float progress) {
        boolean shrink = token.style().equals("shrink");
        float extreme = shrink ? 3.0f : 0.1f;
        return phase == Phase.ENTER ? lerp(extreme, 1f, progress) : lerp(1f, extreme, progress);
    }

    private float computeZoomFactor(AnimationToken token, float progress) {
        boolean zoomIn = token.style().equals("in");
        float extreme = zoomIn ? 0.15f : 3.0f;
        return phase == Phase.ENTER ? lerp(extreme, 1f, progress) : lerp(1f, extreme, progress);
    }

    /** 翻转压缩系数：入场 0 → 1，出场 1 → 0（用 sin 而不是 cos，边缘处不会「消失后重现」）。 */
    private float flipAmount(float progress) {
        float t = phase == Phase.ENTER ? progress : 1f - progress;
        return (float) Math.sin(t * Math.PI / 2);
    }

    /** 旋转角度（度）：入场 -90 → 0，出场 0 → -90。 */
    private float rotateAngle(float progress) {
        return phase == Phase.ENTER ? 90f * (progress - 1f) : -90f * progress;
    }

    private Vec3 computeSlideOffset(AnimationToken token, float progress) {
        float distance = params.scale() * 5f;
        float offset = phase == Phase.ENTER ? lerp(distance, 0f, progress) : lerp(0f, distance, progress);
        return directionVector(token.style()).scale(offset);
    }

    /** 坠落/弹跳：先按方向求位移，bounce 用 easeOutBounce 的轨迹（落地反弹）。 */
    private Vec3 computeDropOffset(AnimationToken token, float progress, boolean bounce) {
        float height = Math.max(1f, params.scale() * 3f);
        float t;
        if (bounce) {
            t = phase == Phase.ENTER ? 1f - Easings.BOUNCE.apply(progress) : Easings.BOUNCE.apply(progress);
        } else {
            float p = phase == Phase.ENTER ? 1f - progress : progress;
            t = p * p;
        }
        return directionVector(token.style()).scale(height * t);
    }

    private Vec3 directionVector(String style) {
        return switch (style) {
            case "up", "rise" -> new Vec3(0, 1, 0);
            case "down", "drop" -> new Vec3(0, -1, 0);
            case "left" -> transformLocalToWorld(-1f, 0f).normalize();
            case "right" -> transformLocalToWorld(1f, 0f).normalize();
            default -> Vec3.ZERO;
        };
    }

    private double scatterDistance(AnimationToken token, float progress) {
        float amount = phase == Phase.ENTER ? 1f - progress : progress;
        double base = Math.max(1f, params.scale()) * Math.max(1f, params.spread()) * 0.7;
        return amount * base;
    }

    private float computeSpacingFactor(AnimationToken token, float progress) {
        boolean converge = token.style().equals("converge");
        float extreme = params.spread();
        if (phase == Phase.ENTER) {
            return converge ? lerp(extreme, 1f, progress) : lerp(1f / extreme, 1f, progress);
        }
        return converge ? lerp(1f, extreme, progress) : lerp(1f, 1f / extreme, progress);
    }

    private Vec3 transformLocalToWorld(float localX, float localY) {
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

        return new Vec3(x3, y2, z3);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
