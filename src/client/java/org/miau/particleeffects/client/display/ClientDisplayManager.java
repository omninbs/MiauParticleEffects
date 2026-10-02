package org.miau.particleeffects.client.display;

import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

        void noteBlockStart(NoteBlockParams params, List<Vec3> anchors);

        void noteBlockStop();

        void noteBlockActivate(int ballIndex, Vec3 target, int note, int durationTicks);
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

    public static void noteBlockStart(NoteBlockParams params, List<Vec3> anchors) {
        backend.noteBlockStart(params, anchors);
    }

    public static void noteBlockStop() {
        backend.noteBlockStop();
    }

    public static void noteBlockActivate(int ballIndex, Vec3 target, int note, int durationTicks) {
        backend.noteBlockActivate(ballIndex, target, note, durationTicks);
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
        public void noteBlockStart(NoteBlockParams params, List<Vec3> anchors) {
            LOGGER.debug("[MiauParticleEffects] noteblock start {} ({} tracks)", params, anchors.size());
        }

        @Override
        public void noteBlockStop() {
            LOGGER.debug("[MiauParticleEffects] noteblock stop");
        }

        @Override
        public void noteBlockActivate(int ballIndex, Vec3 target, int note, int durationTicks) {
            LOGGER.debug("[MiauParticleEffects] ball #{} -> {} (note {}) lead={}",
                    ballIndex, target, note, durationTicks);
        }
    }
}