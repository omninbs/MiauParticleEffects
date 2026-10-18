package org.miau.particleeffects.exparticle.client;

import net.minecraft.client.particle.Particle;
/*? if >=26.1 {*/
import net.minecraft.client.particle.SingleQuadParticle;

import java.lang.reflect.Field;
/*?} else {*/
/*import org.miau.particleeffects.mixin.client.ParticleAccessor;
*//*?}*/

/**
 * 粒子颜色的跨版本读写。
 *
 * <p>1.21.x 的颜色字段在 {@code Particle} 上；26.1 起这些字段下沉到 {@code SingleQuadParticle}，
 * 且只有 {@code setColor} 是公开的，透明度需要反射设置。</p>
 */
public final class ParticleExColor {

    /*? if >=26.1 {*/
    private static final Field RED = find("rCol");
    private static final Field GREEN = find("gCol");
    private static final Field BLUE = find("bCol");
    private static final Field ALPHA = find("alpha");
    /*?}*/

    private ParticleExColor() {
    }

    public static void set(Particle particle, double red, double green, double blue, double alpha) {
        /*? if >=26.1 {*/
        if (particle instanceof SingleQuadParticle quad) {
            quad.setColor((float) red, (float) green, (float) blue);
            write(ALPHA, quad, (float) alpha);
        }
        /*?} else {*/
        /*ParticleAccessor accessor = (ParticleAccessor) particle;
        accessor.exSetColorRed((float) red);
        accessor.exSetColorGreen((float) green);
        accessor.exSetColorBlue((float) blue);
        accessor.exSetAlpha((float) alpha);
        *//*?}*/
    }

    public static float red(Particle particle) {
        /*? if >=26.1 {*/
        return read(RED, particle);
        /*?} else {*/
        /*return ((ParticleAccessor) particle).exGetColorRed();
        *//*?}*/
    }

    public static float green(Particle particle) {
        /*? if >=26.1 {*/
        return read(GREEN, particle);
        /*?} else {*/
        /*return ((ParticleAccessor) particle).exGetColorGreen();
        *//*?}*/
    }

    public static float blue(Particle particle) {
        /*? if >=26.1 {*/
        return read(BLUE, particle);
        /*?} else {*/
        /*return ((ParticleAccessor) particle).exGetColorBlue();
        *//*?}*/
    }

    public static float alpha(Particle particle) {
        /*? if >=26.1 {*/
        return read(ALPHA, particle);
        /*?} else {*/
        /*return ((ParticleAccessor) particle).exGetColorAlpha();
        *//*?}*/
    }

    /*? if >=26.1 {*/
    private static Field find(String name) {
        try {
            Field field = SingleQuadParticle.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static float read(Field field, Particle particle) {
        if (field == null) {
            return 1.0F;
        }
        try {
            return field.getFloat(particle);
        } catch (IllegalAccessException e) {
            return 1.0F;
        }
    }

    private static void write(Field field, Particle particle, float value) {
        if (field == null) {
            return;
        }
        try {
            field.setFloat(particle, value);
        } catch (IllegalAccessException ignored) {
            // 字段不可写时保留原透明度
        }
    }
    /*?}*/
}
