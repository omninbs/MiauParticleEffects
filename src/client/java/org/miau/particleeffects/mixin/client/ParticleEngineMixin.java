package org.miau.particleeffects.mixin.client;

import net.minecraft.client.particle.ParticleEngine;
import org.miau.particleeffects.exparticle.ExParticleConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * 提高原版粒子数量上限与单 tick 更新预算（移植自 ExParticle 的 ParticleManagerMixin，
 * 旧版通过 CoreMod 实现，这里用 Mixin 完成）。
 *
 * < <p>目标常量在各版本可能存在差异，故用 {@code require = 0} 允许静默跳过。</p>
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {

    @ModifyConstant(method = "*", constant = @Constant(intValue = 16384), require = 0)
    private int expe$maxParticleCount(int original) {
        return ExParticleConfig.config.maxParticleCount;
    }

    @ModifyConstant(method = "*", constant = @Constant(longValue = 20L), require = 0)
    private long expe$maxParticleTickMillis(long original) {
        return ExParticleConfig.config.maxParticleTickMillis;
    }
}
