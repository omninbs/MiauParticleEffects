package org.miau.particleeffects.client.particle;

import net.minecraft.client.particle.AnimatedParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.particle.MiauParticleEffectsParticles;

/**
 * 彩色末地烛粒子：与末地烛(end_rod)粒子一致的外观（glitter 精灵、发光、后半生淡出），
 * 颜色可由 spawn 前设置的 {@link #pendingColor} 指定。xRRGGBB）。 *
 * 另有彩虹模式：粒子随世界时间循环改变 HSV 色相（彩虹颜色），各粒子共享同一时间相位。 * 使整段显示整体颜色循环变化。 *
 * 由于 SimpleParticleType 无法携带参数，颜。生命/模式通过 spawn 前的静态槽传递，
 * 生成过程在客户端线程同步执行，工厂读取到的是最近一。spawn 设置的值。 */
public class ColoredEndRodParticle extends AnimatedParticle {

    public static final int MODE_SOLID = 0;
    public static final int MODE_RAINBOW = 1;

    private static int pendingColor = 0xFFFFFF;
    private static int pendingMaxAge = 12;
    private static float pendingScale = 1f;
    private static int pendingMode = MODE_SOLID;
    private static float pendingHueOffset = 0f;

    private final int colorMode;
    private final float hueOffset;

    public ColoredEndRodParticle(ClientWorld world, double x, double y, double z,
                                 double velocityX, double velocityY, double velocityZ,
                                 SpriteProvider spriteProvider) {
        super(world, x, y, z, spriteProvider, 0f);
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.velocityMultiplier = 0.91f;
        this.collidesWithWorld = false;
        this.scale = 0.15f * (random.nextFloat() * 0.5f + 0.5f) * pendingScale;
        this.colorMode = pendingMode;
        this.hueOffset = pendingHueOffset;
        setMaxAge(pendingMaxAge);
        setColor(pendingColor);
    }

    @Override
    public void tick() {
        super.tick();
        if (colorMode == MODE_RAINBOW) {
            float hue = ColorCodec.rainbowPhase(world.getTime()) + hueOffset;
            setColor(ColorCodec.hsvToRgb(hue));
        }
    }

    /**
     * 静态显示：速度为零、短生命（每 tick 重置以保持文字稳定）。     * 默认 maxAge=2：同一时刻。1 层可见粒子，避免动画/移动时出现残影。     */
    public static void spawnStatic(ClientWorld world, double x, double y, double z, int colorArgb) {
        spawnStatic(world, x, y, z, colorArgb, 1f, 2);
    }

    public static void spawnStatic(ClientWorld world, double x, double y, double z,
                                   int colorArgb, float scale) {
        spawnStatic(world, x, y, z, colorArgb, scale, 2);
    }

    public static void spawnStatic(ClientWorld world, double x, double y, double z,
                                   int colorArgb, float scale, int maxAge) {
        pendingMode = MODE_SOLID;
        pendingColor = colorArgb & 0xFFFFFF;
        pendingMaxAge = Math.max(1, maxAge);
        pendingScale = Math.max(0.05f, Math.min(1.5f, scale));
        world.addParticleClient(MiauParticleEffectsParticles.COLORED_END_ROD, x, y, z, 0, 0, 0);
    }

    public static void spawnMoving(ClientWorld world, double x, double y, double z,
                                   double vx, double vy, double vz, int colorArgb, int maxAge) {
        spawnMoving(world, x, y, z, vx, vy, vz, colorArgb, 1f, maxAge);
    }

    public static void spawnMoving(ClientWorld world, double x, double y, double z,
                                   double vx, double vy, double vz, int colorArgb, float scale, int maxAge) {
        pendingMode = MODE_SOLID;
        pendingColor = colorArgb & 0xFFFFFF;
        pendingMaxAge = Math.max(1, maxAge);
        pendingScale = Math.max(0.05f, Math.min(1.5f, scale));
        world.addParticleClient(MiauParticleEffectsParticles.COLORED_END_ROD, x, y, z, vx, vy, vz);
    }

    /**
     * 圆球头粒子（spell 圆形光球精灵）：用于弹力球的“彗星头”，可与拖尾。glitter 星形粒子区分。     */
    public static void spawnHead(ClientWorld world, double x, double y, double z,
                                 double vx, double vy, double vz, int colorArgb, float scale, int maxAge) {
        pendingMode = MODE_SOLID;
        pendingColor = colorArgb & 0xFFFFFF;
        pendingMaxAge = Math.max(1, maxAge);
        pendingScale = Math.max(0.05f, Math.min(4f, scale));
        world.addParticleClient(MiauParticleEffectsParticles.COLORED_HEAD, x, y, z, vx, vy, vz);
    }

    /**
     * 彩虹粒子（整体颜色随世界时间循环 HSV 色相）。headSprite 。true 时使用圆球精灵。     */
    public static void spawnRainbow(ClientWorld world, double x, double y, double z,
                                    double vx, double vy, double vz, float scale, int maxAge,
                                    float hueOffset, boolean headSprite) {
        pendingMode = MODE_RAINBOW;
        pendingColor = 0xFFFFFF;
        pendingMaxAge = Math.max(1, maxAge);
        pendingScale = Math.max(0.05f, Math.min(headSprite ? 4f : 1.5f, scale));
        pendingHueOffset = hueOffset;
        world.addParticleClient(headSprite ? MiauParticleEffectsParticles.COLORED_HEAD : MiauParticleEffectsParticles.COLORED_END_ROD,
                x, y, z, vx, vy, vz);
    }

    public static ParticleFactory<SimpleParticleType> createFactory(SpriteProvider spriteProvider) {
        return (parameters, world, x, y, z, velocityX, velocityY, velocityZ, random) ->
                new ColoredEndRodParticle(world, x, y, z, velocityX, velocityY, velocityZ, spriteProvider);
    }
}
