package org.miau.particleeffects.exparticle.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.exparticle.command.argument.FlipArgumentType;
import org.miau.particleeffects.exparticle.command.argument.ParticleTypeArgument;
import org.miau.particleeffects.exparticle.command.argument.RotateArgumentType;
import org.miau.particleeffects.exparticle.command.argument.Speed3ArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestDoubleArgumentType;
import org.miau.particleeffects.exparticle.command.argument.SuggestIntegerArgumentType;
import org.miau.particleeffects.exparticle.expr.MatrixUtil;
import org.miau.particleeffects.exparticle.network.ExParticleChannels;

/**
 * {@code /mpe particleex image} 与 {@code /mpe particleex imagematrix}：
 * 把 {@code ./particleImages} 下的图片按像素铺成粒子。
 */
public final class ImageCommand {

    private static final String[] MATRIX_SUGGESTS = {
            "E3",
            "E4",
            "\"(1,0,0,0,,0,1,0,0,,0,0,1,-100,,0,0,0,1)\"",
            "\"(0.5,-0.5,0.7071,-0.7071,,0.1464466,0.8535534,0.5,-5,,-0.8535534,-0.1464466,0.5,-5,,0,0,0,1)\""};
    private static final String[] SPEED_EXPRESSION_SUGGESTS =
            {"null", "\"vy=0.1\"", "\"(vx,vy,vz)=((random(),random(),random())-0.5)*t/100\""};

    private ImageCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(image());
        root.then(imageMatrix());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> image() {
        RequiredArgumentBuilder<CommandSourceStack, String> group =
                Commands.argument("group", SuggestArgumentType.argument(StringArgumentType.string(), new String[]{"null"}))
                        .executes(ctx -> runImage(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                DoubleArgumentType.getDouble(ctx, "speedStep"),
                                StringArgumentType.getString(ctx, "group")));

        RequiredArgumentBuilder<CommandSourceStack, Double> speedStep =
                Commands.argument("speedStep", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0F), Double.MAX_VALUE, 1.0))
                        .executes(ctx -> runImage(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                DoubleArgumentType.getDouble(ctx, "speedStep"),
                                null))
                        .then(group);

        RequiredArgumentBuilder<CommandSourceStack, String> speedExpression =
                Commands.argument("speedExpression", SuggestArgumentType.argument(
                                StringArgumentType.string(), SPEED_EXPRESSION_SUGGESTS))
                        .executes(ctx -> runImage(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                1.0, null))
                        .then(speedStep);

        RequiredArgumentBuilder<CommandSourceStack, Integer> age =
                Commands.argument("age", SuggestIntegerArgumentType.integer(-1, Integer.MAX_VALUE, 0))
                        .executes(ctx -> runImage(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                IntegerArgumentType.getInteger(ctx, "age"),
                                null, 1.0, null))
                        .then(speedExpression);

        RequiredArgumentBuilder<CommandSourceStack, Vec3> speed =
                Commands.argument("speed", Speed3ArgumentType.speed3())
                        .executes(ctx -> runImage(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                0, null, 1.0, null))
                        .then(age);

        RequiredArgumentBuilder<CommandSourceStack, Double> dpb =
                Commands.argument("dpb", SuggestDoubleArgumentType.doubleArg(0.0, Double.MAX_VALUE, 10.0))
                        .executes(ctx -> runImage(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Vec3.ZERO, 0, null, 1.0, null))
                        .then(speed);

        RequiredArgumentBuilder<CommandSourceStack, Integer> flip =
                Commands.argument("flip", FlipArgumentType.flip())
                        .executes(ctx -> runImage(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(dpb);

        RequiredArgumentBuilder<CommandSourceStack, Integer> zRotate =
                Commands.argument("zRotate", RotateArgumentType.rotate())
                        .executes(ctx -> runImage(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(flip);

        RequiredArgumentBuilder<CommandSourceStack, Integer> yRotate =
                Commands.argument("yRotate", RotateArgumentType.rotate())
                        .executes(ctx -> runImage(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(zRotate);

        RequiredArgumentBuilder<CommandSourceStack, Integer> xRotate =
                Commands.argument("xRotate", RotateArgumentType.rotate())
                        .executes(ctx -> runImage(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(yRotate);

        RequiredArgumentBuilder<CommandSourceStack, Double> scaling =
                Commands.argument("scaling", SuggestDoubleArgumentType.doubleArg(0.0, Double.MAX_VALUE, 0.1))
                        .executes(ctx -> runImage(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(xRotate);

        RequiredArgumentBuilder<CommandSourceStack, String> path =
                Commands.argument("path", StringArgumentType.string())
                        .executes(ctx -> runImage(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(scaling);

        return Commands.literal("image")
                .then(Commands.argument("name", ParticleTypeArgument.particle())
                        .then(Commands.argument("pos", Vec3Argument.vec3()).then(path)));
    }

    private static int runImage(CommandContext<CommandSourceStack> context, double dpb, Vec3 speed,
                                int age, String speedExpression, double speedStep, String group) {
        ParticleOptions effect = ParticleTypeArgument.getParticle(context, "name");
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        String path = StringArgumentType.getString(context, "path");
        double scaling = has(context, "scaling") ? DoubleArgumentType.getDouble(context, "scaling") : 0.1;
        int xRotate = getInt(context, "xRotate");
        int yRotate = getInt(context, "yRotate");
        int zRotate = getInt(context, "zRotate");
        int flip = getInt(context, "flip");

        ExParticleCommandSupport.send(context.getSource(), ExParticleChannels.IMAGE, buf -> {
            ExParticleCommandSupport.writeType(buf, effect);
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
            buf.writeUtf(path);
            buf.writeDouble(scaling);
            // 竖直翻转等价于再旋转 180 度
            int finalZRotate = flip == 2 ? zRotate + 2 : zRotate;
            buf.writeInt(xRotate);
            buf.writeInt(yRotate);
            buf.writeInt(finalZRotate);
            buf.writeBoolean(flip != 0);
            buf.writeDouble(dpb);
            buf.writeDouble(speed.x);
            buf.writeDouble(speed.y);
            buf.writeDouble(speed.z);
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

    private static LiteralArgumentBuilder<CommandSourceStack> imageMatrix() {
        RequiredArgumentBuilder<CommandSourceStack, String> group =
                Commands.argument("group", SuggestArgumentType.argument(StringArgumentType.string(), new String[]{"null"}))
                        .executes(ctx -> runImageMatrix(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                DoubleArgumentType.getDouble(ctx, "speedStep"),
                                StringArgumentType.getString(ctx, "group")));

        RequiredArgumentBuilder<CommandSourceStack, Double> speedStep =
                Commands.argument("speedStep", SuggestDoubleArgumentType.doubleArg(Math.ulp(0.0F), Double.MAX_VALUE, 1.0))
                        .executes(ctx -> runImageMatrix(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                DoubleArgumentType.getDouble(ctx, "speedStep"),
                                null))
                        .then(group);

        RequiredArgumentBuilder<CommandSourceStack, String> speedExpression =
                Commands.argument("speedExpression", SuggestArgumentType.argument(
                                StringArgumentType.string(), SPEED_EXPRESSION_SUGGESTS))
                        .executes(ctx -> runImageMatrix(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                IntegerArgumentType.getInteger(ctx, "age"),
                                StringArgumentType.getString(ctx, "speedExpression"),
                                1.0, null))
                        .then(speedStep);

        RequiredArgumentBuilder<CommandSourceStack, Integer> age =
                Commands.argument("age", SuggestIntegerArgumentType.integer(-1, Integer.MAX_VALUE, 0))
                        .executes(ctx -> runImageMatrix(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                IntegerArgumentType.getInteger(ctx, "age"),
                                null, 1.0, null))
                        .then(speedExpression);

        RequiredArgumentBuilder<CommandSourceStack, Vec3> speed =
                Commands.argument("speed", Speed3ArgumentType.speed3())
                        .executes(ctx -> runImageMatrix(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Speed3ArgumentType.getSpeed3(ctx, "speed"),
                                0, null, 1.0, null))
                        .then(age);

        RequiredArgumentBuilder<CommandSourceStack, Double> dpb =
                Commands.argument("dpb", SuggestDoubleArgumentType.doubleArg(0.0, Double.MAX_VALUE, 10.0))
                        .executes(ctx -> runImageMatrix(ctx,
                                DoubleArgumentType.getDouble(ctx, "dpb"),
                                Vec3.ZERO, 0, null, 1.0, null))
                        .then(speed);

        RequiredArgumentBuilder<CommandSourceStack, String> matrix =
                Commands.argument("matrix", SuggestArgumentType.argument(StringArgumentType.string(), MATRIX_SUGGESTS))
                        .executes(ctx -> runImageMatrix(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(dpb);

        RequiredArgumentBuilder<CommandSourceStack, Double> scaling =
                Commands.argument("scaling", SuggestDoubleArgumentType.doubleArg(0.0, Double.MAX_VALUE, 0.1))
                        .executes(ctx -> runImageMatrix(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(matrix);

        RequiredArgumentBuilder<CommandSourceStack, String> path =
                Commands.argument("path", StringArgumentType.string())
                        .executes(ctx -> runImageMatrix(ctx, 10.0, Vec3.ZERO, 0, null, 1.0, null))
                        .then(scaling);

        return Commands.literal("imagematrix")
                .then(Commands.argument("name", ParticleTypeArgument.particle())
                        .then(Commands.argument("pos", Vec3Argument.vec3()).then(path)));
    }

    private static int runImageMatrix(CommandContext<CommandSourceStack> context, double dpb, Vec3 speed,
                                      int age, String speedExpression, double speedStep, String group) {
        ParticleOptions effect = ParticleTypeArgument.getParticle(context, "name");
        Vec3 pos = Vec3Argument.getVec3(context, "pos");
        String path = StringArgumentType.getString(context, "path");
        double scaling = has(context, "scaling") ? DoubleArgumentType.getDouble(context, "scaling") : 0.1;
        String matrixStr = has(context, "matrix") ? StringArgumentType.getString(context, "matrix") : "E3";
        double[][] matrix = MatrixUtil.toMat(matrixStr);

        ExParticleCommandSupport.send(context.getSource(), ExParticleChannels.IMAGE_MATRIX, buf -> {
            ExParticleCommandSupport.writeType(buf, effect);
            buf.writeDouble(pos.x);
            buf.writeDouble(pos.y);
            buf.writeDouble(pos.z);
            buf.writeUtf(path);
            buf.writeDouble(scaling);
            buf.writeInt(matrix.length);
            buf.writeInt(matrix.length > 0 ? matrix[0].length : 0);
            for (double[] row : matrix) {
                for (double value : row) {
                    buf.writeDouble(value);
                }
            }
            buf.writeDouble(dpb);
            buf.writeDouble(speed.x);
            buf.writeDouble(speed.y);
            buf.writeDouble(speed.z);
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

    /** 命令树中是否解析到该参数（浅层子命令不会带深层可选参数）。 */
    private static boolean has(CommandContext<CommandSourceStack> context, String name) {
        return context.getNodes().stream()
                .anyMatch(node -> name.equals(node.getNode().getName()));
    }

    private static int getInt(CommandContext<CommandSourceStack> context, String name) {
        return has(context, name) ? IntegerArgumentType.getInteger(context, name) : 0;
    }
}
