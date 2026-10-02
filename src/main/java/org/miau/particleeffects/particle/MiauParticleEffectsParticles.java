package org.miau.particleeffects.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;

/**
 * 自定义粒子类型：彩色末地烛（外观与原。end_rod 完全一致，但可染任意颜色）
 * 与彩色圆球头（使。spell 圆形光球精灵，作为弹力球“彗星头”的头部形状）。 */
public final class MiauParticleEffectsParticles {

    public static final SimpleParticleType COLORED_END_ROD = FabricParticleTypes.simple();
    public static final SimpleParticleType COLORED_HEAD = FabricParticleTypes.simple();

    private MiauParticleEffectsParticles() {
    }

    public static void init() {
        Registry.register(
                BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("miauparticleeffects", "colored_end_rod"),
                COLORED_END_ROD);
        Registry.register(
                BuiltInRegistries.PARTICLE_TYPE,
                Identifier.fromNamespaceAndPath("miauparticleeffects", "colored_head"),
                COLORED_HEAD);
    }
}