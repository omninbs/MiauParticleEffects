package org.miau.particleeffects.exparticle.client;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import org.miau.particleeffects.exparticle.expr.ExprLog;
import org.miau.particleeffects.exparticle.expr.ExpressionUtil;
import org.miau.particleeffects.exparticle.expr.IExecutable;
import org.miau.particleeffects.exparticle.expr.ParticleStruct;
import org.miau.particleeffects.exparticle.network.ExParticleChannels;
import org.miau.particleeffects.exparticle.network.ExParticlePayload;
import org.miau.particleeffects.mixin.client.ParticleAccessor;
import org.miau.particleeffects.mixin.client.ParticleEngineInvoker;

import java.util.Random;

/**
 * 客户端按通道分发 ExParticle 载荷（移植自 ExParticle 的 ClientNetworkHandler）。
 *
 * <p>读取顺序与原实现保持一致；粒子类型用字符串形式传递，解析不到时回退到 {@code crit}。</p>
 */
public final class ExParticleClientNetwork {

    private static final Random RANDOM = new Random();

    private ExParticleClientNetwork() {
    }

    public static void handle(ExParticlePayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload.data()));
        try {
            switch (payload.channel()) {
                case ExParticleChannels.CLEAR_PARTICLE -> clearParticle();
                case ExParticleChannels.CLEAR_CACHE -> ParticleExImageUtil.clear();
                case ExParticleChannels.NORMAL -> normal(buf);
                case ExParticleChannels.CONDITIONAL -> conditional(buf);
                case ExParticleChannels.PARAMETER -> parameter(buf);
                case ExParticleChannels.IMAGE -> image(buf);
                case ExParticleChannels.IMAGE_MATRIX -> imageMatrix(buf);
                case ExParticleChannels.GROUP_REMOVE -> groupRemove(buf);
                case ExParticleChannels.GROUP_CHANGE -> groupChange(buf);
                default -> ExprLog.error(new IllegalArgumentException("未知的 ExParticle 通道: " + payload.channel()));
            }
        } catch (RuntimeException e) {
            ExprLog.error(e);
        }
    }

    /** 读出粒子类型：只支持简单粒子类型，解析失败回退 crit。 */
    private static ParticleOptions readType(FriendlyByteBuf buf) {
        String id = buf.readUtf(128);
        Identifier location = Identifier.tryParse(id);
        if (location != null) {
            ParticleType<?> type = lookup(location);
            if (type instanceof SimpleParticleType simple) {
                return simple;
            }
        }
        return ParticleTypes.CRIT;
    }

    /** 按资源位置查粒子类型；1.21.2 起注册表查询返回 Optional<Reference>。 */
    private static ParticleType<?> lookup(Identifier id) {
        /*? if >=1.21.2 {*/
        return BuiltInRegistries.PARTICLE_TYPE.get(id).map(holder -> holder.value()).orElse(null);
        /*?} else {*/
        /*return BuiltInRegistries.PARTICLE_TYPE.get(id);
        *//*?}*/
    }

    private static void clearParticle() {
        ParticleExGroupUtil.clear();
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            ((ParticleEngineInvoker) client.particleEngine).expe$clearParticles();
        }
    }

    private static void normal(FriendlyByteBuf buf) {
        ParticleOptions effect = readType(buf);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float red = buf.readFloat();
        float green = buf.readFloat();
        float blue = buf.readFloat();
        float alpha = buf.readFloat();
        double vx = buf.readDouble();
        double vy = buf.readDouble();
        double vz = buf.readDouble();
        double dx = buf.readDouble();
        double dy = buf.readDouble();
        double dz = buf.readDouble();
        int count = buf.readInt();
        int age = buf.readInt();
        String expression = null;
        double step = 0.1;
        if (buf.readBoolean()) {
            expression = buf.readUtf();
            step = buf.readDouble();
        }
        String group = null;
        if (buf.readBoolean()) {
            group = buf.readUtf();
        }

        String fe = expression;
        double fs = step;
        String fg = group;
        for (int i = 0; i < count; i++) {
            double rx = RANDOM.nextGaussian() * dx;
            double ry = RANDOM.nextGaussian() * dy;
            double rz = RANDOM.nextGaussian() * dz;
            ParticleExUtil.spawnParticle(effect, x + rx, y + ry, z + rz, x, y, z,
                    red, green, blue, alpha, vx, vy, vz, age, fe, fs, fg);
        }
    }

    private static void conditional(FriendlyByteBuf buf) {
        ParticleOptions effect = readType(buf);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float red = buf.readFloat();
        float green = buf.readFloat();
        float blue = buf.readFloat();
        float alpha = buf.readFloat();
        double vx = buf.readDouble();
        double vy = buf.readDouble();
        double vz = buf.readDouble();
        double dx = buf.readDouble();
        double dy = buf.readDouble();
        double dz = buf.readDouble();
        String expression = null;
        double step = 0.1;
        if (buf.readBoolean()) {
            expression = buf.readUtf();
            step = buf.readDouble();
        }
        int age = buf.readInt();
        String speedExpression = null;
        double speedStep = 0.1;
        if (buf.readBoolean()) {
            speedExpression = buf.readUtf();
            speedStep = buf.readDouble();
        }
        String group = null;
        if (buf.readBoolean()) {
            group = buf.readUtf();
        }

        String fe = speedExpression;
        double fs = speedStep;
        String fg = group;
        IExecutable exe = ExpressionUtil.prase(expression);
        if (exe == null) {
            return;
        }
        ParticleStruct data = exe.getData();
        for (double cx = -dx; cx <= dx; cx += step) {
            for (double cy = -dy; cy <= dy; cy += step) {
                for (double cz = -dz; cz <= dz; cz += step) {
                    data.x = cx;
                    data.y = cy;
                    data.z = cz;
                    data.s1 = Math.atan2(cz, cx);
                    data.s2 = Math.atan2(cy, Math.hypot(cx, cz));
                    data.dis = Math.sqrt(cx * cx + cy * cy + cz * cz);
                    if (exe.invoke() != 0) {
                        ParticleExUtil.spawnParticle(effect, x + cx, y + cy, z + cz, x, y, z,
                                red, green, blue, alpha, vx, vy, vz, age, fe, fs, fg);
                    }
                }
            }
        }
    }

    private static void parameter(FriendlyByteBuf buf) {
        boolean polar = buf.readBoolean();
        boolean tick = buf.readBoolean();
        boolean rgba = buf.readBoolean();
        ParticleOptions effect = readType(buf);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float red = 0.0F;
        float green = 0.0F;
        float blue = 0.0F;
        float alpha = 0.0F;
        if (!rgba) {
            red = buf.readFloat();
            green = buf.readFloat();
            blue = buf.readFloat();
            alpha = buf.readFloat();
        }
        double vx = buf.readDouble();
        double vy = buf.readDouble();
        double vz = buf.readDouble();
        double begin = buf.readDouble();
        double end = buf.readDouble();
        String expression = buf.readUtf();
        double step = buf.readDouble();
        int cpt = 0;
        if (tick) {
            cpt = buf.readInt();
        }
        int age = buf.readInt();
        String speedExpression = null;
        double speedStep = 0.1;
        if (buf.readBoolean()) {
            speedExpression = buf.readUtf();
            speedStep = buf.readDouble();
        }
        String group = null;
        if (buf.readBoolean()) {
            group = buf.readUtf();
        }

        if (tick) {
            if (rgba) {
                ParticleExUtil.spawnTickParticle(effect, x, y, z, vx, vy, vz, begin, end,
                        expression, step, cpt, age, speedExpression, speedStep, group, polar);
            } else {
                ParticleExUtil.spawnTickParticle(effect, x, y, z, red, green, blue, alpha,
                        vx, vy, vz, begin, end, expression, step, cpt, age,
                        speedExpression, speedStep, group, polar);
            }
            return;
        }

        IExecutable exe = ExpressionUtil.prase(expression);
        ParticleStruct data = exe.getData();
        for (double t = begin; t <= end; t += step) {
            data.t = t;
            exe.invoke();
            double dx;
            double dy;
            double dz;
            if (polar) {
                dx = data.dis * Math.cos(data.s2) * Math.cos(data.s1);
                dy = data.dis * Math.sin(data.s2);
                dz = data.dis * Math.cos(data.s2) * Math.sin(data.s1);
            } else {
                dx = data.x;
                dy = data.y;
                dz = data.z;
            }
            if (rgba) {
                ParticleExUtil.spawnParticle(effect, x + dx, y + dy, z + dz, x, y, z,
                        (float) data.cr, (float) data.cg, (float) data.cb, (float) data.alpha,
                        vx, vy, vz, age, speedExpression, speedStep, group);
            } else {
                ParticleExUtil.spawnParticle(effect, x + dx, y + dy, z + dz, x, y, z,
                        red, green, blue, alpha, vx, vy, vz, age,
                        speedExpression, speedStep, group);
            }
        }
    }

    private static void image(FriendlyByteBuf buf) {
        ParticleOptions effect = readType(buf);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        String path = buf.readUtf();
        double scaling = buf.readDouble();
        int xRotate = buf.readInt();
        int yRotate = buf.readInt();
        int zRotate = buf.readInt();
        boolean flip = buf.readBoolean();
        double dpb = buf.readDouble();
        double vx = buf.readDouble();
        double vy = buf.readDouble();
        double vz = buf.readDouble();
        int age = buf.readInt();
        String speedExpression = null;
        double speedStep = 0.1;
        if (buf.readBoolean()) {
            speedExpression = buf.readUtf();
            speedStep = buf.readDouble();
        }
        String group = null;
        if (buf.readBoolean()) {
            group = buf.readUtf();
        }
        ParticleExUtil.spawnImageParticle(effect, x, y, z, path, scaling,
                xRotate, yRotate, zRotate, flip, dpb, vx, vy, vz, age,
                speedExpression, speedStep, group);
    }

    private static void imageMatrix(FriendlyByteBuf buf) {
        ParticleOptions effect = readType(buf);
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        String path = buf.readUtf();
        double scaling = buf.readDouble();
        int rows = buf.readInt();
        int cols = buf.readInt();
        double[][] matrix = new double[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                matrix[row][col] = buf.readDouble();
            }
        }
        double dpb = buf.readDouble();
        double vx = buf.readDouble();
        double vy = buf.readDouble();
        double vz = buf.readDouble();
        int age = buf.readInt();
        String speedExpression = null;
        double speedStep = 0.1;
        if (buf.readBoolean()) {
            speedExpression = buf.readUtf();
            speedStep = buf.readDouble();
        }
        String group = null;
        if (buf.readBoolean()) {
            group = buf.readUtf();
        }
        ParticleExUtil.spawnImageParticle(effect, x, y, z, path, scaling, matrix,
                dpb, vx, vy, vz, age, speedExpression, speedStep, group);
    }

    private static void groupRemove(FriendlyByteBuf buf) {
        String group = buf.readUtf();
        String expression = null;
        if (buf.readBoolean()) {
            expression = buf.readUtf();
        }
        double x;
        double y;
        double z;
        if (buf.readBoolean()) {
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
        } else {
            x = playerX();
            y = playerY();
            z = playerZ();
        }
        ParticleExGroupUtil.remove(group, expression, x, y, z);
    }

    private static void groupChange(FriendlyByteBuf buf) {
        int type = buf.readInt();
        String group = buf.readUtf();
        String expression = buf.readUtf();
        String conditionalExpression = null;
        if (buf.readBoolean()) {
            conditionalExpression = buf.readUtf();
        }
        double x;
        double y;
        double z;
        if (buf.readBoolean()) {
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
        } else {
            x = playerX();
            y = playerY();
            z = playerZ();
        }

        double fx = x;
        double fy = y;
        double fz = z;
        IExecutable exe = ExpressionUtil.prase(expression);
        IExecutable cexe = ExpressionUtil.prase(conditionalExpression);

        for (net.minecraft.client.particle.Particle particle : ParticleExGroupUtil.get(group)) {
            ParticleAccessor accessor = (ParticleAccessor) particle;
            if (cexe != null) {
                ParticleStruct data = cexe.getData();
                double dx = accessor.exGetX() - fx;
                double dy = accessor.exGetY() - fy;
                double dz = accessor.exGetZ() - fz;
                data.x = dx;
                data.y = dy;
                data.z = dz;
                data.s1 = Math.atan2(dz, dx);
                data.s2 = Math.atan2(dy, Math.hypot(dx, dz));
                data.dis = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (cexe.invoke() == 0) {
                    continue;
                }
            }

            if (type == 0) {
                if (exe == null) {
                    continue;
                }
                ParticleStruct data = exe.getData();
                data.x = accessor.exGetX() - fx;
                data.y = accessor.exGetY() - fy;
                data.z = accessor.exGetZ() - fz;
                double prevx = data.vx = accessor.exGetVelocityX();
                double prevy = data.vy = accessor.exGetVelocityY();
                double prevz = data.vz = accessor.exGetVelocityZ();
                IParticle custom = (IParticle) particle;
                data.cx = custom.getCenterX();
                data.cy = custom.getCenterY();
                data.cz = custom.getCenterZ();
                data.cr = ParticleExColor.red(particle);
                data.cg = ParticleExColor.green(particle);
                data.cb = ParticleExColor.blue(particle);
                data.alpha = ParticleExColor.alpha(particle);
                exe.invoke();
                particle.move(data.x - accessor.exGetX() + fx,
                        data.y - accessor.exGetY() + fy,
                        data.z - accessor.exGetZ() + fz);
                custom.setCenterX(data.cx);
                custom.setCenterY(data.cy);
                custom.setCenterZ(data.cz);
                ParticleExColor.set(particle, data.cr, data.cg, data.cb, data.alpha);
                if (data.vx != prevx || data.vy != prevy || data.vz != prevz) {
                    custom.setStop(data.vx == 0.0 && data.vy == 0.0 && data.vz == 0.0);
                }
                accessor.exSetVelocityX(data.vx);
                accessor.exSetVelocityY(data.vy);
                accessor.exSetVelocityZ(data.vz);
            } else if (type == 1) {
                IParticle custom = (IParticle) particle;
                custom.setCustomMove(exe != null);
                custom.setExe(ExpressionUtil.prase(expression));
            }
        }
    }

    private static double playerX() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null ? client.player.getX() : 0.0;
    }

    private static double playerY() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null ? client.player.getY() : 0.0;
    }

    private static double playerZ() {
        Minecraft client = Minecraft.getInstance();
        return client.player != null ? client.player.getZ() : 0.0;
    }
}
