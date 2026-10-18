package org.miau.particleeffects.exparticle.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
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

/** 翻转子指令：not / horizontally / vertical。 */
public class FlipArgumentType implements ArgumentType<Integer> {

    public static final SimpleCommandExceptionType INVALID_EXCEPTION =
            new SimpleCommandExceptionType(Component.translatable("argument.flip.invalid"));

    private static final Collection<String> EXAMPLES = Arrays.asList("not", "horizontally", "vertical");

    public static FlipArgumentType flip() {
        return new FlipArgumentType();
    }

    public static int getFlip(CommandContext<?> context, String name) {
        return context.getArgument(name, Integer.class);
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String flip = reader.readUnquotedString();
        switch (flip) {
            case "not":
                return 0;
            case "horizontally":
                return 1;
            case "vertical":
                return 2;
            default:
                reader.setCursor(start);
                throw INVALID_EXCEPTION.createWithContext(reader);
        }
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider) {
            String remaining = builder.getRemaining();
            for (String example : EXAMPLES) {
                if (example.startsWith(remaining)) {
                    builder.suggest(example);
                }
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
