package org.miau.particleeffects.exparticle.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.exparticle.command.argument.GroupChangeTypeArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestArgumentType;
import org.miau.particleeffects.exparticle.network.ExParticleChannels;

/**
 * {@code /mpe particleex group remove|change}：按组移除或修改已生成的粒子。
 */
public final class GroupCommand {

    private GroupCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("group")
                .then(Commands.literal("remove")
                        .then(Commands.argument("group", StringArgumentType.string())
                                .executes(ctx -> remove(ctx, null, null))
                                .then(Commands.argument("expression", SuggestArgumentType.argument(
                                                StringArgumentType.string(), new String[]{"\"age>100\""}))
                                        .executes(ctx -> remove(ctx,
                                                StringArgumentType.getString(ctx, "expression"), null))
                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                .executes(ctx -> remove(ctx,
                                                        StringArgumentType.getString(ctx, "expression"),
                                                        Vec3Argument.getVec3(ctx, "pos")))))))
                .then(Commands.literal("change")
                        .then(Commands.argument("type", GroupChangeTypeArgumentType.type())
                                .then(Commands.argument("group", StringArgumentType.string())
                                        .then(Commands.argument("expression", SuggestArgumentType.argument(
                                                        StringArgumentType.string(), new String[]{"\"vy=0.1\""}))
                                                .executes(ctx -> change(ctx, null, null))
                                                .then(Commands.argument("conditionalExpression",
                                                                SuggestArgumentType.argument(
                                                                        StringArgumentType.string(),
                                                                        new String[]{"\"age>100\""}))
                                                        .executes(ctx -> change(ctx,
                                                                StringArgumentType.getString(ctx, "conditionalExpression"),
                                                                null))
                                                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                                                .executes(ctx -> change(ctx,
                                                                        StringArgumentType.getString(ctx, "conditionalExpression"),
                                                                        Vec3Argument.getVec3(ctx, "pos"))))))))));
    }

    private static int remove(CommandContext<CommandSourceStack> context, String expression, Vec3 pos) {
        String group = StringArgumentType.getString(context, "group");
        ExParticleCommandSupport.send(context.getSource(), ExParticleChannels.GROUP_REMOVE, buf -> {
            buf.writeUtf(group);
            boolean hasExpression = expression != null && !expression.equals("null");
            buf.writeBoolean(hasExpression);
            if (hasExpression) {
                buf.writeUtf(expression);
            }
            buf.writeBoolean(pos != null);
            if (pos != null) {
                buf.writeDouble(pos.x);
                buf.writeDouble(pos.y);
                buf.writeDouble(pos.z);
            }
        });
        return 1;
    }

    private static int change(CommandContext<CommandSourceStack> context, String conditionalExpression, Vec3 pos) {
        int type = GroupChangeTypeArgumentType.getType(context, "type");
        String group = StringArgumentType.getString(context, "group");
        String expression = StringArgumentType.getString(context, "expression");
        ExParticleCommandSupport.send(context.getSource(), ExParticleChannels.GROUP_CHANGE, buf -> {
            buf.writeInt(type);
            buf.writeUtf(group);
            buf.writeUtf(expression);
            boolean hasConditional = conditionalExpression != null && !conditionalExpression.equals("null");
            buf.writeBoolean(hasConditional);
            if (hasConditional) {
                buf.writeUtf(conditionalExpression);
            }
            buf.writeBoolean(pos != null);
            if (pos != null) {
                buf.writeDouble(pos.x);
                buf.writeDouble(pos.y);
                buf.writeDouble(pos.z);
            }
        });
        return 1;
    }
}
