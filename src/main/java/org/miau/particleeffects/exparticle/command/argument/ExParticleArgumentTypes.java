package org.miau.particleeffects.exparticle.command.argument;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.resources.Identifier;

/**
 * 注册自定义 Brigadier 参数类型的网络序列化器。
 *
 * <p>服务端在玩家登录时会用 {@code ClientboundCommandsPacket} 把整棵命令树发给客户端；
 * 任何未在 {@code ArgumentTypeInfos} 登记的参数类型都会让发包抛异常，玩家随即被踢下线
 * （客户端显示「无效的玩家数据」）。因此这些参数类型必须在主端初始化时注册。
 *
 * <p>这些参数类型都不携带需要同步的状态（客户端只用它们做补全），所以统一使用
 * {@code SingletonArgumentInfo}，网络上不写任何额外字节。</p>
 */
public final class ExParticleArgumentTypes {

    private ExParticleArgumentTypes() {
    }

    public static void register() {
        register("color4", Color4ArgumentType.class,
                SingletonArgumentInfo.contextFree(Color4ArgumentType::color4));
        register("speed3", Speed3ArgumentType.class,
                SingletonArgumentInfo.contextFree(Speed3ArgumentType::speed3));
        register("range3", Range3ArgumentType.class,
                SingletonArgumentInfo.contextFree(Range3ArgumentType::range3));
        register("rotate", RotateArgumentType.class,
                SingletonArgumentInfo.contextFree(RotateArgumentType::rotate));
        register("flip", FlipArgumentType.class,
                SingletonArgumentInfo.contextFree(FlipArgumentType::flip));
        register("group_change_type", GroupChangeTypeArgumentType.class,
                SingletonArgumentInfo.contextFree(GroupChangeTypeArgumentType::type));
        register("particle_type", ParticleTypeArgument.class,
                SingletonArgumentInfo.contextFree(ParticleTypeArgument::particle));
        register("suggest_string", (Class<? extends ArgumentType<?>>) (Class<?>) SuggestArgumentType.class,
                SingletonArgumentInfo.contextFree(
                        () -> SuggestArgumentType.argument(StringArgumentType.string(), new String[]{"null"})));
        register("suggest_double", SuggestDoubleArgumentType.class,
                SingletonArgumentInfo.contextFree(SuggestDoubleArgumentType::doubleArg));
        register("suggest_integer", SuggestIntegerArgumentType.class,
                SingletonArgumentInfo.contextFree(SuggestIntegerArgumentType::integer));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void register(String path, Class<? extends ArgumentType<?>> type,
                                 ArgumentTypeInfo<?, ?> info) {
        ArgumentTypeRegistry.registerArgumentType(
                Identifier.fromNamespaceAndPath("miauparticleeffects", path),
                (Class) type, (ArgumentTypeInfo) info);
    }
}
