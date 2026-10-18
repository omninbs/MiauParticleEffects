package org.miau.particleeffects.exparticle.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.miau.particleeffects.exparticle.network.ExParticleNetwork;

import java.util.function.Consumer;

/** ExParticle 各子命令共用的发送与序列化工具。 */
public final class ExParticleCommandSupport {

    private ExParticleCommandSupport() {
    }

    /** 写入粒子类型：使用资源位置字符串，避免绑定各版本的注册表数字 id。 */
    public static void writeType(FriendlyByteBuf buf, ParticleOptions options) {
        Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType());
        buf.writeUtf(id != null ? id.toString() : "minecraft:crit");
    }

    /** 向同维度玩家广播一条载荷。 */
    public static void send(CommandSourceStack source, String channel, Consumer<FriendlyByteBuf> writer) {
        ExParticleNetwork.send(source.getLevel(), channel, writer);
    }
}
