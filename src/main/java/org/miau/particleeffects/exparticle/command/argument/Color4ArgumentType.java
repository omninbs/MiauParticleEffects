package org.miau.particleeffects.exparticle.command.argument;

import com.google.common.base.Strings;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * 一次读取 4 个空格分隔的浮点数（0~1），移植自 ExParticle 的 Color4ArgumentType。
 */
public class Color4ArgumentType implements ArgumentType<Color4> {

    public static final SimpleCommandExceptionType INCOMPLETE_EXCEPTION =
            new SimpleCommandExceptionType(Component.translatable("argument.color4f.incomplete"));

    private static final FloatArgumentType PARSER = FloatArgumentType.floatArg(0.0F, 1.0F);
    private static final Collection<String> EXAMPLES =
            Arrays.asList("0 0 0 1", "1 1 1 1", "0.5 0.5 0.5 1", "1 0 0 1", "0 1 0 1", "0 0 1 1", "1 1 1 0.5");

    public static Color4ArgumentType color4() {
        return new Color4ArgumentType();
    }

    public static Color4 getColor4(CommandContext<?> context, String name) {
        return context.getArgument(name, Color4.class);
    }

    @Override
    public Color4 parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        float red = PARSER.parse(reader);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            float green = PARSER.parse(reader);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                float blue = PARSER.parse(reader);
                if (reader.canRead() && reader.peek() == ' ') {
                    reader.skip();
                    float alpha = PARSER.parse(reader);
                    return new Color4(red, green, blue, alpha);
                }
            }
        }
        reader.setCursor(start);
        throw INCOMPLETE_EXCEPTION.createWithContext(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider) {
            String remaining = builder.getRemaining();
            if (Strings.isNullOrEmpty(remaining)) {
                builder.suggest("1");
                builder.suggest("1 1");
                builder.suggest("1 1 1");
                builder.suggest("1 1 1 1");
                return builder.buildFuture();
            }
            String[] args = remaining.split(" ");
            if (args.length == 1) {
                builder.suggest(args[0] + " 1");
                builder.suggest(args[0] + " 1 1");
                builder.suggest(args[0] + " 1 1 1");
            } else if (args.length == 2) {
                builder.suggest(String.join(" ", args) + " 1");
                builder.suggest(String.join(" ", args) + " 1 1");
            } else if (args.length == 3) {
                builder.suggest(String.join(" ", args) + " 1");
            }
            return builder.buildFuture();
        }
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
