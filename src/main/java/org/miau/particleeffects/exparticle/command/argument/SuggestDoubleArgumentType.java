package org.miau.particleeffects.exparticle.command.argument;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/** 双精度参数，附加一个补全建议值。 */
public class SuggestDoubleArgumentType implements ArgumentType<Double> {

    private final DoubleArgumentType parser;
    private final double suggest;

    public SuggestDoubleArgumentType(double min, double max, double suggest) {
        this.parser = DoubleArgumentType.doubleArg(min, max);
        this.suggest = suggest;
    }

    public static SuggestDoubleArgumentType doubleArg() {
        return new SuggestDoubleArgumentType(Double.MIN_VALUE, Double.MAX_VALUE, 0.0);
    }

    public static SuggestDoubleArgumentType doubleArg(double min) {
        return new SuggestDoubleArgumentType(min, Double.MAX_VALUE, 0.0);
    }

    public static SuggestDoubleArgumentType doubleArg(double min, double max) {
        return new SuggestDoubleArgumentType(min, max, 0.0);
    }

    public static SuggestDoubleArgumentType doubleArg(double min, double max, double suggest) {
        return new SuggestDoubleArgumentType(min, max, suggest);
    }

    public double getMinimum() {
        return parser.getMinimum();
    }

    public double getMaximum() {
        return parser.getMaximum();
    }

    public double getSuggest() {
        return suggest;
    }

    @Override
    public Double parse(StringReader reader) throws CommandSyntaxException {
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
        if (!(other instanceof SuggestDoubleArgumentType that)) {
            return false;
        }
        return parser.equals(that.parser) && suggest == that.suggest;
    }

    @Override
    public int hashCode() {
        return (int) (31 * parser.hashCode() + suggest);
    }

    @Override
    public String toString() {
        if (parser.getMinimum() == Double.MIN_VALUE && parser.getMaximum() == Double.MAX_VALUE && suggest == 0.0) {
            return "suggestDouble()";
        }
        if (parser.getMaximum() == Double.MAX_VALUE && suggest == 0.0) {
            return "suggestDouble(" + parser.getMinimum() + ")";
        }
        return suggest == 0.0
                ? "suggestDouble(" + parser.getMinimum() + ", " + parser.getMaximum() + ")"
                : "suggestDouble(" + parser.getMinimum() + ", " + parser.getMaximum() + ", " + suggest + ")";
    }
}
