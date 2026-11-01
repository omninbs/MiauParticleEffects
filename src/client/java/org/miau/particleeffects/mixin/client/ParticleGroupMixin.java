package org.miau.particleeffects.mixin.client;

import org.miau.particleeffects.exparticle.ExParticleConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/*? if >=1.21.11 {*/
import net.minecraft.client.particle.ParticleGroup;

/**
 * 1.21.11 起原版把粒子分层上限从 {@code ParticleEngine} 移到了 {@code ParticleGroup}
 * （按 {@code ParticleRenderType} 分组，所有粒子与原版共享 SINGLE_QUADS 组）：
 *
 * <ul>
 *   <li><b>1.21.11</b>：每组一个 {@code EvictingQueue}，容量 {@code MAX_PARTICLES = 16384}，
 *       满员时静默淘汰最旧粒子（还活着的粒子直接消失）；</li>
 *   <li><b>26.2</b>：改为 {@code ArrayDeque}，{@code add()} 里超过 16384 直接拒绝；
 *       且在 [12288, 16384) 区间按 {@code ((16384 - size) / 4096)<sup>2</sup>} 的概率随机丢弃
 *       —— 表现为粒子「溶解」式的随机缺失。</li>
 * </ul>
 *
 * <p>旧的 {@link ParticleEngineMixin} 只能命中 1.21.10 及以下 Engine 内的常量，
 * 新版下会静默失配（{@code require = 0}），导致上限无法调整。这里把
 * {@code ParticleGroup} 中的 16384 统一替换为配置值
 * {@link ExParticleConfig.ConfigData#maxParticleCount}：</p>
 *
 * <ul>
 *   <li>26.2 中「溶解概率 = ((上限 - size) / 4096)²」的分母不变，稀释区间随之整体上移
 *       到 [上限-4096, 上限)，行为与原版一致只是容量更大；</li>
 *   <li>构造函数里的 16384 只是集合初始容量，一并替换无害。</li>
 * </ul>
 *
 * <p>1.21.10 及以下不存在该类，整个文件块由 Stonecutter 条件编译移除，
 * 对应 mixins.json 里的 {@code ParticleGroupMixin} 条目由构建脚本按版本注入。</p>
 */
@Mixin(ParticleGroup.class)
public abstract class ParticleGroupMixin {

    @ModifyConstant(method = "*", constant = @Constant(intValue = 16384), require = 0)
    private int expe$maxParticleCount(int original) {
        return ExParticleConfig.config.maxParticleCount;
    }
}
/*?}*/
