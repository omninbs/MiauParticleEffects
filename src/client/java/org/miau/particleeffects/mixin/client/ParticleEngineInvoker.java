package org.miau.particleeffects.mixin.client;

import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code ParticleEngine} 的私有清空方法（原 ExParticle 中该方法是公开的，
 * 现代版本改成了私有，这里通过 Invoker 桥接）。
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineInvoker {

    @Invoker("clearParticles")
    void expe$clearParticles();
}
