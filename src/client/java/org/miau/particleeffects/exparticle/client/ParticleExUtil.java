package org.miau.particleeffects.exparticle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import org.miau.particleeffects.exparticle.expr.ExprLog;
import org.miau.particleeffects.exparticle.expr.ExpressionUtil;
import org.miau.particleeffects.exparticle.expr.IExecutable;
import org.miau.particleeffects.exparticle.expr.MatrixUtil;
import org.miau.particleeffects.exparticle.expr.ParticleStruct;
import org.miau.particleeffects.mixin.client.ParticleAccessor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * 粒子生成与按 tick 排程的工具（移植自 ExParticle 的 ParticleUtil）。
 */
public final class ParticleExUtil {

    private static final Queue<Runnable> TICK_START_TASKS = new ArrayDeque<>();
    private static final Queue<Runnable> TICK_END_TASKS = new ArrayDeque<>();

    private ParticleExUtil() {
    }

    /** 生成单颗粒子，可由表达式驱动其位置。 */
    public static void spawnParticle(
            ParticleOptions effect,
            double x, double y, double z,
            double cx, double cy, double cz,
            float red, float green, float blue, float alpha,
            double vx, double vy, double vz,
            int age, String expression, double step, String group) {
        try {
            Particle particle = Minecraft.getInstance().particleEngine
                    .createParticle(effect, x, y, z, vx, vy, vz);
            if (particle == null) {
                return;
            }
            ParticleAccessor accessor = (ParticleAccessor) particle;
            ParticleExColor.set(particle, red, green, blue, alpha);
            IParticle custom = (IParticle) particle;
            if (vx == 0.0 && vy == 0.0 && vz == 0.0) {
                custom.setStop(true);
            } else {
                custom.setStop(false);
                accessor.exSetVelocityX(vx);
                accessor.exSetVelocityY(vy);
                accessor.exSetVelocityZ(vz);
            }
            custom.setCenterX(cx);
            custom.setCenterY(cy);
            custom.setCenterZ(cz);
            if (age > 0) {
                particle.setLifetime(age);
            } else if (age == -1) {
                particle.setLifetime(Integer.MAX_VALUE);
            }
            if (expression != null && !expression.equals("null")) {
                custom.setExe(ExpressionUtil.prase(expression));
                custom.setStep(step);
                custom.setCustomMove(true);
            }
            ParticleExGroupUtil.add(group, particle);
        } catch (RuntimeException e) {
            ExprLog.error(e);
        }
    }

    /** 带 RGB 颜色的按 tick 排程粒子生成。 */
    public static void spawnTickParticle(
            ParticleOptions effect,
            double x, double y, double z,
            float red, float green, float blue, float alpha,
            double vx, double vy, double vz,
            double begin, double end,
            String expression, double step, int cpt, int age,
            String speedExpression, double speedStep, String group, boolean polar) {
        new TickParticleTask(effect, x, y, z, red, green, blue, alpha, vx, vy, vz,
                begin, end, expression, step, cpt, age, speedExpression, speedStep, group, polar, false).run();
    }

    /** 颜色由表达式给出的按 tick 排程粒子生成。 */
    public static void spawnTickParticle(
            ParticleOptions effect,
            double x, double y, double z,
            double vx, double vy, double vz,
            double begin, double end,
            String expression, double step, int cpt, int age,
            String speedExpression, double speedStep, String group, boolean polar) {
        new TickParticleTask(effect, x, y, z, 1.0F, 1.0F, 1.0F, 1.0F, vx, vy, vz,
                begin, end, expression, step, cpt, age, speedExpression, speedStep, group, polar, true).run();
    }

    /** 按图片生成粒子（整数角度旋转）。 */
    public static void spawnImageParticle(
            ParticleOptions effect,
            double x, double y, double z,
            String path, double scaling,
            int xRotate, int yRotate, int zRotate, boolean flip,
            double dpb, double vx, double vy, double vz, int age,
            String speedExpression, double speedStep, String group) {
        spawnImageParticle(effect, x, y, z, path, scaling, xRotate, yRotate, zRotate, flip,
                null, dpb, vx, vy, vz, age, speedExpression, speedStep, group);
    }

    /** 按图片生成粒子（外部矩阵变换）。 */
    public static void spawnImageParticle(
            ParticleOptions effect,
            double x, double y, double z,
            String path, double scaling, double[][] matrix,
            double dpb, double vx, double vy, double vz, int maxAge,
            String speedExpression, double speedStep, String group) {
        spawnImageParticle(effect, x, y, z, path, scaling, 0, 0, 0, false,
                matrix, dpb, vx, vy, vz, maxAge, speedExpression, speedStep, group);
    }

    /** 按图片生成粒子（可同时使用整数角度旋转与外部矩阵）。 */
    public static void spawnImageParticle(
            ParticleOptions effect,
            double x, double y, double z,
            String path, double scaling,
            int xRotate, int yRotate, int zRotate, boolean flip, double[][] matrix,
            double dpb, double vx, double vy, double vz, int age,
            String speedExpression, double speedStep, String group) {
        try {
            BufferedImage image = ParticleExImageUtil.readImage(path, scaling, true);
            int rows = image.getHeight();
            int cols = image.getWidth();
            int[][] rotateFlipMat = getRotateFlipMat(xRotate, yRotate, zRotate, flip, rows, cols);

            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int pixel = image.getRGB(col, row);
                    float alpha = ((pixel & 0xFF000000) >>> 24) / 255.0F;
                    float red = ((pixel & 0xFF0000) >>> 16) / 255.0F;
                    float green = ((pixel & 0xFF00) >>> 8) / 255.0F;
                    float blue = (pixel & 0xFF) / 255.0F;
                    if (alpha == 0.0F) {
                        continue;
                    }
                    double[][] pos = MatrixUtil.matDiv(
                            MatrixUtil.matMul(rotateFlipMat, new int[][]{{col}, {row}, {0}, {1}}), dpb);
                    if (matrix != null) {
                        pos = MatrixUtil.matMul(matrix, pos);
                    }
                    double dx = pos[0][0];
                    double dy = pos[1][0];
                    double dz = pos[2][0];
                    spawnParticle(effect, x + dx, y + dy, z + dz, x, y, z,
                            red, green, blue, alpha, vx, vy, vz, age,
                            speedExpression, speedStep, group);
                }
            }
        } catch (IOException e) {
            ExprLog.error(e);
        }
    }

    private static int[][] getRotateFlipMat(int xRotate, int yRotate, int zRotate, boolean flip, int rows, int cols) {
        int[][] flipmat = new int[][]{
                {flip ? -1 : 1, 0, 0, flip ? cols - 1 : 0},
                {0, -1, 0, rows - 1},
                {0, 0, 1, 0},
                {0, 0, 0, 1}};
        int[][] zmat = new int[][]{
                {dcos(zRotate), -dsin(zRotate), 0, xmove(zRotate, rows, cols)},
                {dsin(zRotate), dcos(zRotate), 0, ymove(zRotate, rows, cols)},
                {0, 0, 1, 0},
                {0, 0, 0, 1}};
        int[][] ymat = new int[][]{
                {dcos(yRotate), 0, dsin(yRotate), 0},
                {0, 1, 0, 0},
                {-dsin(yRotate), 0, dcos(yRotate), 0},
                {0, 0, 0, 1}};
        int[][] xmat = new int[][]{
                {1, 0, 0, 0},
                {0, dcos(xRotate), dsin(xRotate), 0},
                {0, -dsin(xRotate), dcos(xRotate), 0},
                {0, 0, 0, 1}};
        return MatrixUtil.matMul(xmat, MatrixUtil.matMul(ymat, MatrixUtil.matMul(zmat, flipmat)));
    }

    private static int dsin(int n) {
        switch (n % 4) {
            case 0:
                return 0;
            case 1:
                return 1;
            case 2:
                return 0;
            case 3:
                return -1;
            default:
                return n;
        }
    }

    private static int dcos(int n) {
        switch (n % 4) {
            case 0:
                return 1;
            case 1:
                return 0;
            case 2:
                return -1;
            case 3:
                return 0;
            default:
                return n;
        }
    }

    private static int xmove(int rotate, int rows, int cols) {
        switch (rotate % 4) {
            case 0:
                return 0;
            case 1:
                return rows - 1;
            case 2:
                return cols - 1;
            case 3:
                return 0;
            default:
                return rotate;
        }
    }

    private static int ymove(int rotate, int rows, int cols) {
        switch (rotate % 4) {
            case 0:
                return 0;
            case 1:
                return 0;
            case 2:
                return rows - 1;
            case 3:
                return cols - 1;
            default:
                return rotate;
        }
    }

    private static void addTask(Runnable task, boolean start) {
        if (start) {
            synchronized (TICK_START_TASKS) {
                TICK_START_TASKS.add(task);
            }
        } else {
            synchronized (TICK_END_TASKS) {
                TICK_END_TASKS.add(task);
            }
        }
    }

    /** 每个客户端 tick 开始时执行排程任务。 */
    public static void onStartClientTick() {
        synchronized (TICK_START_TASKS) {
            while (!TICK_START_TASKS.isEmpty()) {
                TICK_START_TASKS.poll().run();
            }
        }
    }

    /** 每个客户端 tick 结束时执行排程任务。 */
    public static void onEndClientTick() {
        synchronized (TICK_END_TASKS) {
            while (!TICK_END_TASKS.isEmpty()) {
                TICK_END_TASKS.poll().run();
            }
        }
    }

    /** 把任务推迟到下一 tick 开头执行。 */
    private record TickEndTask(Runnable nextTask) implements Runnable {
        @Override
        public void run() {
            addTask(this.nextTask, true);
        }
    }

    /** 在若干 tick 内分帧生成粒子的排程任务。 */
    private static final class TickParticleTask implements Runnable {

        private final ParticleOptions particleType;
        private final double x;
        private final double y;
        private final double z;
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;
        private final double vx;
        private final double vy;
        private final double vz;
        private final IExecutable exe;
        private final double step;
        private final int cpt;
        private final double end;
        private final int age;
        private final String speedExpression;
        private final double speedStep;
        private final String group;
        private final boolean polar;
        private final boolean rgba;
        private double t;

        private TickParticleTask(
                ParticleOptions particleType,
                double x, double y, double z,
                float red, float green, float blue, float alpha,
                double vx, double vy, double vz,
                double begin, double end,
                String expression, double step, int cpt, int age,
                String speedExpression, double speedStep, String group,
                boolean polar, boolean rgba) {
            this.polar = polar;
            this.rgba = rgba;
            this.particleType = particleType;
            this.x = x;
            this.y = y;
            this.z = z;
            this.t = begin;
            this.end = end;
            this.exe = ExpressionUtil.prase(expression);
            this.step = step;
            this.cpt = cpt;
            this.age = age;
            this.speedExpression = speedExpression;
            this.speedStep = speedStep;
            this.group = group;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
        }

        @Override
        public void run() {
            ParticleStruct data = this.exe.getData();

            int i = 0;
            for (; i < this.cpt && this.t <= this.end; this.t = this.t + this.step) {
                data.t = this.t;
                this.exe.invoke();
                double dx;
                double dy;
                double dz;
                if (this.polar) {
                    dx = data.dis * Math.cos(data.s2) * Math.cos(data.s1);
                    dy = data.dis * Math.sin(data.s2);
                    dz = data.dis * Math.cos(data.s2) * Math.sin(data.s1);
                } else {
                    dx = data.x;
                    dy = data.y;
                    dz = data.z;
                }

                if (this.rgba) {
                    spawnParticle(this.particleType, this.x + dx, this.y + dy, this.z + dz,
                            this.x, this.y, this.z,
                            (float) data.cr, (float) data.cg, (float) data.cb, (float) data.alpha,
                            data.vx, data.vy, data.vz, this.age,
                            this.speedExpression, this.speedStep, this.group);
                } else {
                    spawnParticle(this.particleType, this.x + dx, this.y + dy, this.z + dz,
                            this.x, this.y, this.z,
                            this.red, this.green, this.blue, this.alpha,
                            this.vx, this.vy, this.vz, this.age,
                            this.speedExpression, this.speedStep, this.group);
                }
            }

            if (this.t <= this.end) {
                addTask(new TickEndTask(this), false);
            }
        }
    }
}
