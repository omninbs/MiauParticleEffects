package org.miau.particleeffects.exparticle.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/** 包装任意参数类型，并额外提供固定的补全候选。 */
public class SuggestArgumentType<TT extends ArgumentType<T>, T> implements ArgumentType<T> {

    private final TT parser;
    private final T[] suggests;

    public SuggestArgumentType(TT parser, T[] suggests) {
        this.parser = parser;
        this.suggests = suggests;
    }

    public static <TT extends ArgumentType<T>, T> SuggestArgumentType<TT, T> argument(TT parser, T[] suggests) {
        return new SuggestArgumentType<>(parser, suggests);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T parse(StringReader reader) throws CommandSyntaxException {
        return parser.parse(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider) {
            String remaining = builder.getRemaining();
            for (T suggest : suggests) {
                String text = suggest.toString();
                if (text.startsWith(remaining)) {
                    builder.suggest(text);
                }
            }
            return builder.buildFuture();
        }
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return parser.getExamples();
    }
}
