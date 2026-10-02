package org.miau.particleeffects.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

public final class Feedback {

    private Feedback() {
    }

    public static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(Component.literal("§c[mpe] " + message)).create();
    }

    public static void send(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("§7[mpe] §f" + message), false);
    }

    public static void warn(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal("§e[mpe] §f" + message), false);
    }
}