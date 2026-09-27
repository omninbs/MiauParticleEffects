package org.miau.particleeffects.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public final class Feedback {

    private Feedback() {
    }

    public static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Text.literal("§c[mpe] " + message)).create();
    }

    public static void send(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal("§7[mpe] §f" + message), false);
    }

    public static void warn(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal("§e[mpe] §f" + message), false);
    }
}