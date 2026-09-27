package org.miau.particleeffects.client.display;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.Vec3d;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端显示门面：客户端收包入口将数据交给这里。Backend。 *
 * 渲染、文字栅格化、状态机、弹力球轨迹模拟等由显示后端实现
 * （通过 {@link #setBackend(Backend)} 注入）。 */
public final class ClientDisplayManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("miauparticleeffects");
    private static Backend backend = new NoopBackend();

    private ClientDisplayManager() {
    }

    public interface Backend {
        void showText(TextDisplayParams params);

        void showEffect(EffectDisplayParams params);

        void clear(ClearScope scope, String id);

        void noteBlockStart(NoteBlockParams params);

        void noteBlockStop();

        void noteBlockActivate(int ballIndex, Vec3d target, int note);
    }

    public static void setBackend(Backend newBackend) {
        backend = newBackend != null ? newBackend : new NoopBackend();
    }

    public static void showText(TextDisplayParams params) {
        backend.showText(params);
    }

    public static void showEffect(EffectDisplayParams params) {
        backend.showEffect(params);
    }

    public static void clear(ClearScope scope, String id) {
        backend.clear(scope, id);
    }

    public static void noteBlockStart(NoteBlockParams params) {
        backend.noteBlockStart(params);
    }

    public static void noteBlockStop() {
        backend.noteBlockStop();
    }

    public static void noteBlockActivate(int ballIndex, Vec3d target, int note) {
        backend.noteBlockActivate(ballIndex, target, note);
    }

    /**
     * 弹力球落地：由显示后端在客户端完成落地判定后调用，上报服务端以触发下一波分配。     */
    public static void reportBallLanded(int ballIndex) {
        ClientPlayNetworking.send(new MiauParticleEffectsPackets.BallLandedC2SPayload(ballIndex));
    }

    private static final class NoopBackend implements Backend {
        @Override
        public void showText(TextDisplayParams params) {
            LOGGER.debug("[MiauParticleEffects] showText ^{}@{} id={}",
                    params.text(), params.pos(), params.id());
        }

        @Override
        public void showEffect(EffectDisplayParams params) {
            LOGGER.debug("[MiauParticleEffects] showEffect {}@{} id={}",
                    params.type(), params.pos(), params.id());
        }

        @Override
        public void clear(ClearScope scope, String id) {
            LOGGER.debug("[MiauParticleEffects] clear scope={} id={}", scope, id);
        }

        @Override
        public void noteBlockStart(NoteBlockParams params) {
            LOGGER.debug("[MiauParticleEffects] noteblock start {}", params);
        }

        @Override
        public void noteBlockStop() {
            LOGGER.debug("[MiauParticleEffects] noteblock stop");
        }

        @Override
        public void noteBlockActivate(int ballIndex, Vec3d target, int note) {
            LOGGER.debug("[MiauParticleEffects] ball #{} -> {} (note {})", ballIndex, target, note);
        }
    }
}