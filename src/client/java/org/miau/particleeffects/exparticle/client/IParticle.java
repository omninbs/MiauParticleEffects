package org.miau.particleeffects.exparticle.client;

import org.miau.particleeffects.exparticle.expr.IExecutable;

/**
 * 由 {@code ParticleMixin} 注入到原版粒子上的自定义行为接口（移植自 ExParticle 的 IParticle）。
 *
 * <p>原版粒子通过本接口被表达式驱动：位置不再遵循原版速度积分，而由 {@code exe} 每 tick 求出。</p>
 */
public interface IParticle {

    void setExe(IExecutable exe);

    void setStep(double step);

    double getCenterX();

    void setCenterX(double centerX);

    double getCenterY();

    void setCenterY(double centerY);

    double getCenterZ();

    void setCenterZ(double centerZ);

    void setCustomMove(boolean customMove);

    /** 速度为零时置为 true：保留原版 tick 但不产生位移。 */
    void setStop(boolean stop);
}
