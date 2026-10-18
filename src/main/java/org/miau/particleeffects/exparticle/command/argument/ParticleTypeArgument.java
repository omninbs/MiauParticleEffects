package org.miau.particleeffects.exparticle.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * 粒子类型参数：只接受简单粒子类型（与原 ExParticle 使用的 ParticleArgument 行为一致）。
 *
 * <p>不用原版 {@code ParticleArgument} 是因为它在不同版本的签名不一致（有的版本需要
 * {@code CommandBuildContext}）；这里直接查内置注册表。</p>
 */
public class ParticleTypeArgument implements ArgumentType<ParticleOptions> {

    public static final DynamicCommandExceptionType INVALID_PARTICLE =
            new DynamicCommandExceptionType(id -> Component.literal("未知的粒子类型: " + id));

    public static ParticleTypeArgument particle() {
        return new ParticleTypeArgument();
    }

    public static ParticleOptions getParticle(CommandContext<?> context, String name) {
        return context.getArgument(name, ParticleOptions.class);
    }

    @Override
    public ParticleOptions parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String raw = reader.readUnquotedString();
        Identifier id = Identifier.tryParse(raw);
        if (id != null) {
            ParticleType<?> type = lookup(id);
            if (type instanceof SimpleParticleType simple) {
                return simple;
            }
        }
        reader.setCursor(start);
        throw INVALID_PARTICLE.createWithContext(reader, raw);
    }

    /** 按资源位置查粒子类型；1.21.2 起注册表查询返回 Optional<Reference>。 */
    private static ParticleType<?> lookup(Identifier id) {
        /*? if >=1.21.2 {*/
        return BuiltInRegistries.PARTICLE_TYPE.get(id).map(holder -> holder.value()).orElse(null);
        /*?} else {*/
        /*return BuiltInRegistries.PARTICLE_TYPE.get(id);
        *//*?}*/
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(getSimpleParticleIds(), builder);
    }

    /** 所有简单粒子类型的资源位置字符串，供补全使用。 */
    public static Collection<String> getSimpleParticleIds() {
        return BuiltInRegistries.PARTICLE_TYPE.keySet().stream()
                .filter(id -> lookup(id) instanceof SimpleParticleType)
                .map(Identifier::toString)
                .toList();
    }
}
