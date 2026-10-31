package org.miau.particleeffects.mixin.client;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 {@code ParticleEngine} 的私有清空方法（原 ExParticle 中该方法是公开的，
 * 现代版本改成了私有，这里通过 Invoker 桥接）。
 *
 * <p>同时暴露 {@code add}，让模组可以直接把构造好的粒子塞进引擎：
 * 这样可绕过 {@code ClientLevel#addParticle} 里按「粒子效果」视频设置做的随机抽样，
 * 也不会因注册表查找与 provider 分发产生额外开销。</p>
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineInvoker {

    @Invoker("clearParticles")
    void expe$clearParticles();

    @Invoker("add")
    void expe$addParticle(Particle particle);
}
