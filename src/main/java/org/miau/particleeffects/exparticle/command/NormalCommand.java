package org.miau.particleeffects.exparticle.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.exparticle.command.argument.Color4;
import org.miau.particleeffects.exparticle.command.argument.Color4ArgumentType;
import org.miau.particleeffects.exparticle.command.argument.ParticleTypeArgument;
import org.miau.particleeffects.exparticle.command.argument.Range3ArgumentType;
import org.miau.particleeffects.exparticle.command.argument.Speed3ArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestDoubleArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestIntegerArgumentType;
import org.miau.particleeffects.exparticle.network.ExParticleChannels;

/**
 * {@code /mpe particleex normal}：在指定位置按正态分布撒一批粒子。
 */
public final class NormalCommand {

    private static final String[] SPEED_EXPRESSION_SUGGESTS =
            {"null", "\"vy=0.1\"", "\"(vx,vy,vz)=((random(),random(),random())-0.5)*t/100\""};

    private NormalCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        RequiredArgumentBuilder<CommandSourceStack, String> group =
                Commands.argument("group", SuggestArgumentType.argument(StringArgumentType.string(), new String[]{"null"}))
                        .executes(ctx -> run(ctx, getAge(ctx),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                DoubleArgumentType.getDouble(ctx, "speedStep"),
                                StringArgumentType.getString(ctx, "group")));

        RequiredArgumentBuilder<CommandSourceStack, Double> speedStep =
                Commands.argument("speedStep", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0F), Double.MAX_VALUE, 1.0))
                        .executes(ctx -> run(ctx, getAge(ctx),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                DoubleArgumentType.getDouble(ctx, "speedStep"),
                                null))
                        .then(group);

        RequiredArgumentBuilder<CommandSourceStack, String> speedExpression =
                Commands.argument("speedExpression", SuggestArgumentType.argument(
                                StringArgumentType.string(), SPEED_EXPRESSION_SUGGESTS))
                        .executes(ctx -> run(ctx, getAge(ctx),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                1.0, null))
                        .then(speedStep);

        RequiredArgumentBuilder<CommandSourceStack, Integer> age =
                Commands.argument("age", SuggestIntegerArgumentType.integer(-1, Integer.MAX_VALUE, 0))
                        .executes(ctx -> run(ctx, getAge(ctx), null, 1.0, null))
                        .then(speedExpression);

        RequiredArgumentBuilder<CommandSourceStack, Integer> count =
                Commands.argument("count", SuggestIntegerArgumentType.integer(0, Integer.MAX_VALUE, 1))
                        .executes(ctx -> run(ctx, 0, null, 1.0, null))
                        .then(age);

        RequiredArgumentBuilder<CommandSourceStack, Vec3> range =
                Commands.argument("range", Range3ArgumentType.range3()).then(count);

        RequiredArgumentBuilder<CommandSourceStack, Vec3> speed =
                Commands.argument("speed", Speed3ArgumentType.speed3()).then(range);

        RequiredArgumentBuilder<CommandSourceStack, Color4> color =
                Commands.argument("color", Color4ArgumentType.color4()).then(speed);

        RequiredArgumentBuilder<CommandSourceStack, Coordinates> pos =
                Commands.argument("pos", Vec3Argument.vec3()).then(color);

        root.then(Commands.literal("normal")
                .then(Commands.argument("name", ParticleTypeArgument.particle()).then(pos)));
    }

    private static int getAge(CommandContext<CommandSourceStack> context) {
        return IntegerArgumentType.getInteger(context, "age");
    }

    private static int run(CommandContext<CommandSourceStack> context, int age,
                           String expression, double step, String group) {
        ParticleOptions effect = ParticleTypeArgument.getParticle(context, "name");
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        Color4 color = Color4ArgumentType.getColor4(context, "color");
        Vec3 speed = Speed3ArgumentType.getSpeed3(context, "speed");
        Vec3 range = Range3ArgumentType.getRange3(context, "range");
        int count = IntegerArgumentType.getInteger(context, "count");

        ExParticleCommandSupport.send(context.getSource(), ExParticleChannels.NORMAL, buf -> {
            ExParticleCommandSupport.writeType(buf, effect);
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
            buf.writeFloat(color.red());
            buf.writeFloat(color.green());
            buf.writeFloat(color.blue());
            buf.writeFloat(color.alpha());
            buf.writeDouble(speed.x);
            buf.writeDouble(speed.y);
            buf.writeDouble(speed.z);
            buf.writeDouble(range.x);
            buf.writeDouble(range.y);
            buf.writeDouble(range.z);
            buf.writeInt(count);
            buf.writeInt(age);
            boolean hasExpression = expression != null && !expression.equals("null");
            buf.writeBoolean(hasExpression);
            if (hasExpression) {
                buf.writeUtf(expression);
                buf.writeDouble(step);
            }
            boolean hasGroup = group != null && !group.equals("null");
            buf.writeBoolean(hasGroup);
            if (hasGroup) {
                buf.writeUtf(group);
            }
        });
        return 1;
    }
}
