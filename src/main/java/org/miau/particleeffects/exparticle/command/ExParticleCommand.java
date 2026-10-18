package org.miau.particleeffects.exparticle.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.miau.particleeffects.exparticle.network.ExParticleChannels;

/**
 * {@code /mpe particleex} 的根节点与无参子命令（移植自 ExParticle 的 ParticleExCommand）。
 */
public final class ExParticleCommand {

    private static final String FUNCTION_LIST = "sin(a), cos(a), tan(a), asin(a), acos(a), atan(a), "
            + "toRadians(angdeg), toDegrees(angrad), exp(a), log(a), log10(a), sqrt(a), cbrt(a), "
            + "IEEEremainder(f1, f2), ceil(a), floor(a), rint(a), atan2(y, x), pow(a, b), round(a), random(), "
            + "addExact(x, y), subtractExact(x, y), multiplyExact(x, y), incrementExact(a), decrementExact(a), "
            + "negateExact(a), floorDiv(x, y), floorMod(x, y), abs(a), max(a, b), min(a, b), ulp(d), signum(d), "
            + "sinh(x), cosh(x), tanh(x), hypot(x, y), expm1(x), log1p(x), copySign(magnitude, sign), "
            + "getExponent(d), nextAfter(start, direction), nextUp(d), nextDown(d), scalb(d, scaleFactor), powerOfTwoD(n)";

    private ExParticleCommand() {
    }

    /** 构建 particleex 节点；权限沿用父节点 /mpe 的等级检查。 */
    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("particleex");
        root.then(Commands.literal("functionlist").executes(ExParticleCommand::functionList));
        root.then(Commands.literal("clearparticle").executes(ctx -> {
            ExParticleCommandSupport.send(ctx.getSource(), ExParticleChannels.CLEAR_PARTICLE, buf -> {
            });
            return 1;
        }));
        root.then(Commands.literal("clearcache").executes(ctx -> {
            ExParticleCommandSupport.send(ctx.getSource(), ExParticleChannels.CLEAR_CACHE, buf -> {
            });
            return 1;
        }));
        NormalCommand.register(root);
        ConditionalCommand.register(root);
        ParameterCommand.register(root);
        ImageCommand.register(root);
        GroupCommand.register(root);
        return root;
    }

    private static int functionList(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(FUNCTION_LIST), false);
        return 1;
    }
}
