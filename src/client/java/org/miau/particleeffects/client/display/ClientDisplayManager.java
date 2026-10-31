package org.miau.particleeffects.client.display;

import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.NoteChainPayload;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.TextChainPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 客户端显示门面：客户端收包入口将数据交给这里。Backend。 *
 * 渲染、文字栅格化、状态机、音符链时间轴播放等由显示后端实现
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

        /** 收到整条音符链时间轴：开始（或按 id 重置）本地播放。 */
        void noteChain(NoteChainPayload payload);

        /** 移除链：id 为 null 表示移除全部。 */
        void noteChainClear(String id);

        /** 收到整条歌词链时间轴：按表在各自时刻显示文字。 */
        void textChain(TextChainPayload payload);

        /** 移除歌词链：id 为 null 表示移除全部。 */
        void textChainClear(String id);
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

    public static void noteChain(NoteChainPayload payload) {
        backend.noteChain(payload);
    }

    public static void noteChainClear(String id) {
        backend.noteChainClear(id);
    }

    public static void textChain(TextChainPayload payload) {
        backend.textChain(payload);
    }

    public static void textChainClear(String id) {
        backend.textChainClear(id);
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
        public void noteChain(NoteChainPayload payload) {
            LOGGER.debug("[MiauParticleEffects] noteChain id={} notes={}",
                    payload.id(), payload.notes().size());
        }

        @Override
        public void noteChainClear(String id) {
            LOGGER.debug("[MiauParticleEffects] noteChainClear id={}", id);
        }

        @Override
        public void textChain(TextChainPayload payload) {
            LOGGER.debug("[MiauParticleEffects] textChain id={} entries={}",
                    payload.id(), payload.entries().size());
        }

        @Override
        public void textChainClear(String id) {
            LOGGER.debug("[MiauParticleEffects] textChainClear id={}", id);
        }
    }
}
