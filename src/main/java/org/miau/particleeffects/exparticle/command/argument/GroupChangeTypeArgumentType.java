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

/** 分组修改类型：parameter（直接改参数）/ speedexpression（改速度表达式）。 */
public class GroupChangeTypeArgumentType implements ArgumentType<Integer> {

    public static final SimpleCommandExceptionType INVALID_EXCEPTION =
            new SimpleCommandExceptionType(Component.translatable("argument.group.change.type.invalid"));

    private static final Collection<String> EXAMPLES = Arrays.asList("parameter", "speedexpression");

    public static GroupChangeTypeArgumentType type() {
        return new GroupChangeTypeArgumentType();
    }

    public static int getType(CommandContext<?> context, String name) {
        return context.getArgument(name, Integer.class);
    }

    @Override
    public Integer parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String type = reader.readUnquotedString();
        switch (type) {
            case "parameter":
                return 0;
            case "speedexpression":
                return 1;
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
