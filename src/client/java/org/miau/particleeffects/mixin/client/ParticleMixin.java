package org.miau.particleeffects.mixin.client;

import net.minecraft.client.particle.Particle;
import org.miau.particleeffects.exparticle.client.IParticle;
import org.miau.particleeffects.exparticle.client.ParticleExColor;
import org.miau.particleeffects.exparticle.expr.ExprLog;
import org.miau.particleeffects.exparticle.expr.IExecutable;
import org.miau.particleeffects.exparticle.expr.ParticleStruct;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让原版粒子可被表达式驱动（移植自 ExParticle 的 ParticleMixin）。
 *
 * <p>原实现用 {@code @Redirect} 包住 {@code ParticleEngine} 对 {@code Particle.tick()} 的调用；
 * 这里改为在 {@code Particle.tick()} 自身的 HEAD/RETURN 处注入，效果等价且不依赖
 * {@code ParticleEngine} 的内部方法名（各版本差异较大）。</p>
 *
 * <p>当粒子被设为「自定义移动」或「静止」时，先保存位置/速度，执行原版 tick（保留碰撞、寿命、
 * 淡出等行为），再回滚位置/速度，最后由表达式重新求位置。</p>
 */
@Mixin(Particle.class)
public abstract class ParticleMixin implements IParticle {

    @Shadow
    protected double x;
    @Shadow
    protected double y;
    @Shadow
    protected double z;
    @Shadow
    protected double xd;
    @Shadow
    protected double yd;
    @Shadow
    protected double zd;

    @Shadow
    public abstract void move(double x, double y, double z);

    @Shadow
    public abstract void remove();

    private IExecutable expe$exe;
    private double expe$step;
    private double expe$centerX;
    private double expe$centerY;
    private double expe$centerZ;
    private boolean expe$customMove;
    private boolean expe$stop;
    private double expe$moveT;
    private double expe$preX;
    private double expe$preY;
    private double expe$preZ;
    private double expe$preVx;
    private double expe$preVy;
    private double expe$preVz;

    @Override
    public void setExe(IExecutable exe) {
        this.expe$exe = exe;
    }

    @Override
    public void setStep(double step) {
        this.expe$step = step;
    }

    @Override
    public double getCenterX() {
        return this.expe$centerX;
    }

    @Override
    public void setCenterX(double centerX) {
        this.expe$centerX = centerX;
    }

    @Override
    public double getCenterY() {
        return this.expe$centerY;
    }

    @Override
    public void setCenterY(double centerY) {
        this.expe$centerY = centerY;
    }

    @Override
    public double getCenterZ() {
        return this.expe$centerZ;
    }

    @Override
    public void setCenterZ(double centerZ) {
        this.expe$centerZ = centerZ;
    }

    @Override
    public void setCustomMove(boolean customMove) {
        this.expe$customMove = customMove;
    }

    @Override
    public void setStop(boolean stop) {
        this.expe$stop = stop;
    }

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void expe$preTick(CallbackInfo ci) {
        if (this.expe$customMove || this.expe$stop) {
            this.expe$preX = this.x;
            this.expe$preY = this.y;
            this.expe$preZ = this.z;
            this.expe$preVx = this.xd;
            this.expe$preVy = this.yd;
            this.expe$preVz = this.zd;
        }
    }

    @Inject(method = "tick()V", at = @At("RETURN"))
    private void expe$postTick(CallbackInfo ci) {
        if (this.expe$customMove || this.expe$stop) {
            this.x = this.expe$preX;
            this.y = this.expe$preY;
            this.z = this.expe$preZ;
            this.xd = this.expe$preVx;
            this.yd = this.expe$preVy;
            this.zd = this.expe$preVz;
            this.expe$customMove();
        }
    }

    private void expe$customMove() {
        if (!this.expe$customMove || this.expe$exe == null) {
            return;
        }
        ParticleStruct data = this.expe$exe.getData();
        double dx = this.x - this.expe$centerX;
        double dy = this.y - this.expe$centerY;
        double dz = this.z - this.expe$centerZ;
        if (this.expe$moveT == 0.0) {
            data.vx = this.xd;
            data.vy = this.yd;
            data.vz = this.zd;
            data.cx = this.expe$centerX;
            data.cy = this.expe$centerY;
            data.cz = this.expe$centerZ;
            data.dx = dx;
            data.dy = dy;
            data.dz = dz;
            data.ddis = Math.sqrt(dx * dx + dy * dy + dz * dz);
            data.ds1 = Math.atan2(dz, dx);
            data.ds2 = Math.atan2(dy, Math.hypot(dx, dz));
            data.cr = ParticleExColor.red((Particle) (Object) this);
            data.cg = ParticleExColor.green((Particle) (Object) this);
            data.cb = ParticleExColor.blue((Particle) (Object) this);
            data.alpha = ParticleExColor.alpha((Particle) (Object) this);
        }

        data.x = dx;
        data.y = dy;
        data.z = dz;
        data.dis = Math.sqrt(dx * dx + dy * dy + dz * dz);
        data.s1 = Math.atan2(dz, dx);
        data.s2 = Math.atan2(dy, Math.hypot(dx, dz));
        data.t = this.expe$moveT;
        this.expe$moveT = this.expe$moveT + this.expe$step;

        try {
            this.expe$exe.invoke();
        } catch (RuntimeException e) {
            ExprLog.error(e);
        }

        if (data.destory != 0.0) {
            this.remove();
            return;
        }

        this.move(data.vx, data.vy, data.vz);
        ParticleExColor.set((Particle) (Object) this, data.cr, data.cg, data.cb, data.alpha);
    }
}
