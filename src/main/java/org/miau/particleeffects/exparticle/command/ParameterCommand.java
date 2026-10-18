package org.miau.particleeffects.exparticle.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
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
import org.miau.particleeffects.exparticle.command.argument.Speed3ArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestDoubleArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestIntegerArgumentType;
import org.miau.particleeffects.exparticle.network.ExParticleChannels;

/**
 * {@code /mpe particleex parameter|polarparameter|tickparameter|tickpolarparameter|
 * rgbaparameter|rgbapolarparameter|rgbatickparameter|rgbatickpolarparameter}。
 *
 * <p>8 个子命令的节点结构一致，仅在三开关（polar / tick / rgba）与是否带 color 上不同，
 * 因此用同一套构建逻辑生成。</p>
 */
public final class ParameterCommand {

    private static final String[] XY_SUGGESTS =
            {"null", "\"x,y=t,sin(t)\"", "\"x=t;y=t^2\""};
    private static final String[] POLAR_SUGGESTS =
            {"null", "\"s1,s2,dis=t*10,t*PI/20,1\""};
    private static final String[] RGBA_XY_SUGGESTS =
            {"null", "\"x,y,cr,cg,cb=t,sin(t),sin(t/7)/4+0.75,sin(t/5)/4+0.75,sin(t/3)/4+0.75\""};
    private static final String[] RGBA_POLAR_SUGGESTS =
            {"null", "\"s1,s2,dis=t*10,t*PI/20,1;cr,cg,cb=sin(t/7)/4+0.75,sin(t/5)/4+0.75,sin(t/3)/4+0.75\""};
    private static final String[] SPEED_EXPRESSION_SUGGESTS =
            {"null", "\"vy=0.1\"", "\"(vx,vy,vz)=((random(),random(),random())-0.5)*t/100\""};

    private ParameterCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(build("parameter", false, false, false, XY_SUGGESTS));
        root.then(build("polarparameter", true, false, false, POLAR_SUGGESTS));
        root.then(build("tickparameter", false, true, false, XY_SUGGESTS));
        root.then(build("tickpolarparameter", true, true, false, POLAR_SUGGESTS));
        root.then(build("rgbaparameter", false, false, true, RGBA_XY_SUGGESTS));
        root.then(build("rgbapolarparameter", true, false, true, RGBA_POLAR_SUGGESTS));
        root.then(build("rgbatickparameter", false, true, true, RGBA_XY_SUGGESTS));
        root.then(build("rgbatickpolarparameter", true, true, true, RGBA_POLAR_SUGGESTS));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(
            String name, boolean polar, boolean tick, boolean rgba, String[] suggests) {
        // group -> 叶子
        RequiredArgumentBuilder<CommandSourceStack, String> group =
                Commands.argument("group", SuggestArgumentType.argument(StringArgumentType.string(), new String[]{"null"}))
                        .executes(ctx -> run(ctx, polar, tick, rgba,
                                DoubleArgumentType.getDouble(ctx, "step"),
                                tick ? IntegerArgumentType.getInteger(ctx, "cpt") : 0,
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                DoubleArgumentType.getDouble(ctx, "speedStep"),
                                StringArgumentType.getString(ctx, "group")));

        RequiredArgumentBuilder<CommandSourceStack, Double> speedStep =
                Commands.argument("speedStep", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0F), Double.MAX_VALUE, 1.0))
                        .executes(ctx -> run(ctx, polar, tick, rgba,
                                DoubleArgumentType.getDouble(ctx, "step"),
                                tick ? IntegerArgumentType.getInteger(ctx, "cpt") : 0,
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                DoubleArgumentType.getDouble(ctx, "speedStep"),
                                null))
                        .then(group);

        RequiredArgumentBuilder<CommandSourceStack, String> speedExpression =
                Commands.argument("speedExpression", SuggestArgumentType.argument(
                                StringArgumentType.string(), SPEED_EXPRESSION_SUGGESTS))
                        .executes(ctx -> run(ctx, polar, tick, rgba,
                                DoubleArgumentType.getDouble(ctx, "step"),
                                tick ? IntegerArgumentType.getInteger(ctx, "cpt") : 0,
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                1.0, null))
                        .then(speedStep);

        RequiredArgumentBuilder<CommandSourceStack, Integer> age =
                Commands.argument("age", SuggestIntegerArgumentType.integer(-1, Integer.MAX_VALUE, 0))
                        .executes(ctx -> run(ctx, polar, tick, rgba,
                                DoubleArgumentType.getDouble(ctx, "step"),
                                tick ? IntegerArgumentType.getInteger(ctx, "cpt") : 0,
                                IntegerArgumentType.getInteger(ctx, "age"),
                                null, 1.0, null))
                        .then(speedExpression);

        ArgumentBuilder<CommandSourceStack, ?> afterStep;
        if (tick) {
            afterStep = Commands.argument("cpt", SuggestIntegerArgumentType.integer(1, Integer.MAX_VALUE, 10))
                    .executes(ctx -> run(ctx, polar, tick, rgba,
                            DoubleArgumentType.getDouble(ctx, "step"),
                            IntegerArgumentType.getInteger(ctx, "cpt"),
                            0, null, 1.0, null))
                    .then(age);
        } else {
            afterStep = age;
        }

        RequiredArgumentBuilder<CommandSourceStack, Double> step =
                Commands.argument("step", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0F), Double.MAX_VALUE, 0.1))
                        .executes(ctx -> run(ctx, polar, tick, rgba,
                                DoubleArgumentType.getDouble(ctx, "step"),
                                0, 0, null, 1.0, null))
                        .then(afterStep);

        RequiredArgumentBuilder<CommandSourceStack, String> expression =
                Commands.argument("expression", SuggestArgumentType.argument(StringArgumentType.string(), suggests))
                        .executes(ctx -> run(ctx, polar, tick, rgba, 0.1, 0, 0, null, 1.0, null))
                        .then(step);

        RequiredArgumentBuilder<CommandSourceStack, Double> end =
                Commands.argument("end", SuggestDoubleArgumentType.doubleArg(
                                -Double.MAX_VALUE, Double.MAX_VALUE, 10.0))
                        .then(expression);

        RequiredArgumentBuilder<CommandSourceStack, Double> begin =
                Commands.argument("begin", SuggestDoubleArgumentType.doubleArg(
                                -Double.MAX_VALUE, Double.MAX_VALUE, -10.0))
                        .then(end);

        RequiredArgumentBuilder<CommandSourceStack, Vec3> speed =
                Commands.argument("speed", Speed3ArgumentType.speed3()).then(begin);

        ArgumentBuilder<CommandSourceStack, ?> afterPos = speed;
        if (!rgba) {
            afterPos = Commands.argument("color", Color4ArgumentType.color4()).then(speed);
        }

        RequiredArgumentBuilder<CommandSourceStack, Coordinates> pos =
                Commands.argument("pos", Vec3Argument.vec3()).then(afterPos);

        return Commands.literal(name)
                .then(Commands.argument("name", ParticleTypeArgument.particle()).then(pos));
    }

    private static int run(CommandContext<CommandSourceStack> context, boolean polar, boolean tick, boolean rgba,
                           double step, int cpt, int age, String speedExpression, double speedStep, String group) {
        ParticleOptions effect = ParticleTypeArgument.getParticle(context, "name");
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        Color4 color = rgba ? null : Color4ArgumentType.getColor4(context, "color");
        Vec3 speed = Speed3ArgumentType.getSpeed3(context, "speed");
        double begin = DoubleArgumentType.getDouble(context, "begin");
        double end = DoubleArgumentType.getDouble(context, "end");
        String expression = StringArgumentType.getString(context, "expression");

        ExParticleCommandSupport.send(context.getSource(), ExParticleChannels.PARAMETER, buf -> {
            buf.writeBoolean(polar);
            buf.writeBoolean(tick);
            buf.writeBoolean(rgba);
            ExParticleCommandSupport.writeType(buf, effect);
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
            if (!rgba) {
                buf.writeFloat(color.red());
                buf.writeFloat(color.green());
                buf.writeFloat(color.blue());
                buf.writeFloat(color.alpha());
            }
            buf.writeDouble(speed.x);
            buf.writeDouble(speed.y);
            buf.writeDouble(speed.z);
            buf.writeDouble(begin);
            buf.writeDouble(end);
            buf.writeUtf(expression);
            buf.writeDouble(step);
            if (tick) {
                buf.writeInt(cpt);
            }
            buf.writeInt(age);
            boolean hasSpeedExpression = speedExpression != null && !speedExpression.equals("null");
            buf.writeBoolean(hasSpeedExpression);
            if (hasSpeedExpression) {
                buf.writeUtf(speedExpression);
                buf.writeDouble(speedStep);
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
