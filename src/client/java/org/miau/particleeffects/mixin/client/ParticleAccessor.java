package org.miau.particleeffects.mixin.client;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 原版粒子内部字段的访问器（移植自 ExParticle 的 IParticleMixin）。
 *
 * <p>方法名统一加 {@code ex} 前缀，避免与目标类已有方法冲突。</p>
 */
@Mixin(Particle.class)
public interface ParticleAccessor {

    @Accessor("x")
    double exGetX();

    @Accessor("y")
    double exGetY();

    @Accessor("z")
    double exGetZ();

    @Accessor("xd")
    double exGetVelocityX();

    @Accessor("xd")
    void exSetVelocityX(double value);

    @Accessor("yd")
    double exGetVelocityY();

    @Accessor("yd")
    void exSetVelocityY(double value);

    @Accessor("zd")
    double exGetVelocityZ();

    @Accessor("zd")
    void exSetVelocityZ(double value);

    @Accessor("age")
    int exGetAge();

    // 26.1 起颜色字段下沉到 SingleQuadParticle；低版本代码必须保持注释，
    // 因为 active 版本（26.2）直接编译 src，不经过条件预处理。
    /*? if <26.1 {*/
    /*@Accessor("rCol")
    float exGetColorRed();

    @Accessor("rCol")
    void exSetColorRed(float value);

    @Accessor("gCol")
    float exGetColorGreen();

    @Accessor("gCol")
    void exSetColorGreen(float value);

    @Accessor("bCol")
    float exGetColorBlue();

    @Accessor("bCol")
    void exSetColorBlue(float value);

    @Accessor("alpha")
    float exGetColorAlpha();

    @Accessor("alpha")
    void exSetAlpha(float value);
    *//*?}*/
}
