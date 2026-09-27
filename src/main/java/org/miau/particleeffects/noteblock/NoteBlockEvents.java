package org.miau.particleeffects.noteblock;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 音符盒激活事件分发点。 *
 * 服务端混入（。NoteblockBlock 的激活方法，由其他模块实现）在音符盒被激活时调用
 * {@link #publish(ServerWorld, BlockPos, int)}。当前开启音符盒特效时，
 * NoteBlockRuntime 会被注册为监听者并按半径过滤后分发到客户端弹力球。 */
public final class NoteBlockEvents {

    private static final Set<NoteBlockListener> LISTENERS =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private NoteBlockEvents() {
    }

    public static void register(NoteBlockListener listener) {
        LISTENERS.add(listener);
    }

    public static void unregister(NoteBlockListener listener) {
        LISTENERS.remove(listener);
    }

    public static boolean hasListeners() {
        return !LISTENERS.isEmpty();
    }

    public static void publish(ServerWorld world, BlockPos pos, int pitch) {
        if (LISTENERS.isEmpty()) {
            return;
        }
        for (NoteBlockListener listener : Set.copyOf(LISTENERS)) {
            try {
                listener.onNoteBlock(world, pos, pitch);
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger("miauparticleeffects")
                        .warn("音符盒事件处理失败", e);
            }
        }
    }
}