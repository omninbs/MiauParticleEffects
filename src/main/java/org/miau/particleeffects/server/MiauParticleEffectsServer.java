package org.miau.particleeffects.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import org.miau.particleeffects.config.ConfigManager;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.noteblock.NoteBlockEvents;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端编排：记录显示实例（供清除/自动清除），管理音符盒运行时，广播数据包。
 */
public final class MiauParticleEffectsServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("miauparticleeffects");
    private static final AtomicLong ID_SEQ = new AtomicLong();
    private static final Map<String, ActiveInstance> ACTIVE = new LinkedHashMap<>();
    private static NoteBlockRuntime noteBlockRuntime;

    private enum ActiveKind {
        TEXT,
        EFFECT
    }

    static {
        // 单一全局服务端 tick 钩子：把音符盒 tick 内的批量目标在 tick 末分发给弹力球。
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (noteBlockRuntime != null) {
                noteBlockRuntime.tick(server);
            }
        });
    }

    private record ActiveInstance(String id, ActiveKind kind, long expireTick, int exitTicks) {
        boolean isActive(long now) {
            return now < expireTick;
        }
    }

    private MiauParticleEffectsServer() {
    }

    public static String showText(CommandSourceStack source, TextDisplayParams paramsIn) {
        MinecraftServer server = source.getServer();
        long now = server.getTickCount();
        prune(server);
        String id = assignId(paramsIn.id());
        TextDisplayParams params = paramsIn.withId(id);

        if (ConfigManager.get().autoclear) {
            List<ActiveInstance> texts = ACTIVE.values().stream()
                    .filter(a -> a.kind() == ActiveKind.TEXT && a.isActive(now))
                    .toList();
            if (!texts.isEmpty()) {
                int maxExit = 0;
                for (ActiveInstance a : texts) {
                    maxExit = Math.max(maxExit, a.exitTicks());
                    ACTIVE.remove(a.id());
                }
                MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.ClearPayload(ClearScope.TEXT, null));
                params = params.withDelay(maxExit);
            }
        }

        ACTIVE.put(id, new ActiveInstance(id, ActiveKind.TEXT,
                now + params.totalTicks(), params.exitTicks()));
        MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.ShowTextPayload(params));
        return id;
    }

    public static String showEffect(CommandSourceStack source, EffectDisplayParams paramsIn) {
        MinecraftServer server = source.getServer();
        long now = server.getTickCount();
        prune(server);
        String id = assignId(paramsIn.id());
        EffectDisplayParams params = paramsIn.withId(id);
        ACTIVE.put(id, new ActiveInstance(id, ActiveKind.EFFECT,
                now + params.totalTicks(), params.exitTicks()));
        MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.ShowEffectPayload(params));
        return id;
    }

    public static int clear(CommandSourceStack source, ClearScope scope, String id) {
        MinecraftServer server = source.getServer();
        long now = server.getTickCount();
        if (scope == ClearScope.ALL) {
            int removed = ACTIVE.size();
            ACTIVE.clear();
            MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.ClearPayload(ClearScope.ALL, null));
            return removed;
        }
        ActiveKind kind = scope == ClearScope.TEXT ? ActiveKind.TEXT : ActiveKind.EFFECT;
        int removed = 0;
        var it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().kind() == kind) {
                it.remove();
                removed++;
            }
        }
        MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.ClearPayload(scope, null));
        return removed;
    }

    public static boolean clearById(MinecraftServer server, String id) {
        if (server == null || id == null) {
            return false;
        }
        ActiveInstance removed = ACTIVE.remove(id);
        if (removed == null) {
            return false;
        }
        MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.ClearPayload(ClearScope.ALL, id));
        return true;
    }

    /** 已识别出的轨道数量（= 弹力球数量）。 */
    public static int noteBlockTrackCount() {
        return NoteTrackManager.trackCount();
    }

    public static void noteBlockOn(MinecraftServer server, NoteBlockParams paramsIn) {
        if (noteBlockRuntime != null) {
            NoteBlockEvents.unregister(noteBlockRuntime);
        }
        // 一颗弹力球对应一条轨道。
        NoteBlockParams params = paramsIn.withBallCount(NoteTrackManager.trackCount());
        NoteBlockRuntime runtime = new NoteBlockRuntime(params, NoteTrackManager.tracks());
        runtime.bindServer(server);
        noteBlockRuntime = runtime;
        NoteBlockEvents.register(runtime);
        MiauParticleEffectsPackets.broadcast(server,
                new MiauParticleEffectsPackets.NoteBlockStartPayload(params, NoteTrackManager.anchors()));
    }

    public static boolean noteBlockOff(MinecraftServer server) {
        if (noteBlockRuntime == null) {
            return false;
        }
        NoteBlockEvents.unregister(noteBlockRuntime);
        noteBlockRuntime = null;
        MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.NoteBlockStopPayload());
        return true;
    }

    public static boolean noteBlockSet(MinecraftServer server, NoteBlockParams params) {
        if (noteBlockRuntime == null) {
            return false;
        }
        noteBlockOn(server, params);
        return true;
    }

    public static NoteBlockParams noteBlockStatus() {
        return noteBlockRuntime != null ? noteBlockRuntime.config() : null;
    }

    /** 重新选择轨道后，若特效正在运行，用新轨道立即重启。 */
    public static void refreshNoteBlockTracks(MinecraftServer server) {
        if (noteBlockRuntime == null) {
            return;
        }
        noteBlockOn(server, noteBlockRuntime.config());
    }

    private static String assignId(String requested) {
        if (requested != null && !requested.isBlank() && !ACTIVE.containsKey(requested)) {
            return requested;
        }
        String id;
        do {
            id = "auto-" + ID_SEQ.incrementAndGet();
        } while (ACTIVE.containsKey(id));
        return id;
    }

    /**
     * 移除已自然到期的显示记录，避免 ACTIVE 无限增长、自定义 id 无法复用。
     */
    private static void prune(MinecraftServer server) {
        long now = server.getTickCount();
        ACTIVE.entrySet().removeIf(entry -> !entry.getValue().isActive(now));
    }
}