package org.miau.particleeffects.exparticle.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/** 整数参数，附加一个补全建议值。 */
public class SuggestIntegerArgumentType implements ArgumentType<Integer> {

    private final IntegerArgumentType parser;
    private final int suggest;

    public SuggestIntegerArgumentType(int min, int max, int suggest) {
        this.parser = IntegerArgumentType.integer(min, max);
        this.suggest = suggest;
    }

    public static SuggestIntegerArgumentType integer() {
        return new SuggestIntegerArgumentType(Integer.MIN_VALUE, Integer.MAX_VALUE, 0);
    }

    public static SuggestIntegerArgumentType integer(int min) {
        return new SuggestIntegerArgumentType(min, Integer.MAX_VALUE, 0);
    }

    public static SuggestIntegerArgumentType integer(int min, int max) {
        return new SuggestIntegerArgumentType(min, max, 0);
    }

    public static SuggestIntegerArgumentType integer(int min, int max, int suggest) {
        return new SuggestIntegerArgumentType(min, max, suggest);
    }

    public int getMinimum() {
        return parser.getMinimum();
    }

    public int getMaximum() {
        return parser.getMaximum();
    }

    public int getSuggest() {
        return suggest;
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        return parser.parse(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        if (context.getSource() instanceof SharedSuggestionProvider
                && String.valueOf(suggest).startsWith(builder.getRemaining())) {
            return builder.suggest(String.valueOf(suggest)).buildFuture();
        }
        return Suggestions.empty();
    }

    @Override
    public Collection<String> getExamples() {
        return parser.getExamples();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SuggestIntegerArgumentType that)) {
            return false;
        }
        return parser.equals(that.parser) && suggest == that.suggest;
    }

    @Override
    public int hashCode() {
        return 31 * parser.hashCode() + suggest;
    }

    @Override
    public String toString() {
        if (parser.getMinimum() == Integer.MIN_VALUE && parser.getMaximum() == Integer.MAX_VALUE && suggest == 0) {
            return "suggestInteger()";
        }
        if (parser.getMaximum() == Integer.MAX_VALUE && suggest == 0) {
            return "suggestInteger(" + parser.getMinimum() + ")";
        }
        return suggest == 0
                ? "suggestInteger(" + parser.getMinimum() + ", " + parser.getMaximum() + ")"
                : "suggestInteger(" + parser.getMinimum() + ", " + parser.getMaximum() + ", " + suggest + ")";
    }
}
