package org.miau.particleeffects.particle;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

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
                Registries.PARTICLE_TYPE,
                Identifier.of("miauparticleeffects", "colored_end_rod"),
                COLORED_END_ROD);
        Registry.register(
                Registries.PARTICLE_TYPE,
                Identifier.of("miauparticleeffects", "colored_head"),
                COLORED_HEAD);
    }
}