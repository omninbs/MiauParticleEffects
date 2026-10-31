package org.miau.particleeffects.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.config.ConfigManager;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端编排：记录显示实例（供清除/自动清除），维护存活的音符链，广播数据包。
 *
 * 音符链采用「整条时间轴一次性下发」：服务端只负责登记存活链（供 clear/status）并广播，
 * 不做逐音符调度——时序完全由生成器给出，客户端本地按表播放。
 */
public final class MiauParticleEffectsServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("miauparticleeffects");
    private static final AtomicLong ID_SEQ = new AtomicLong();
    private static final Map<String, ActiveInstance> ACTIVE = new LinkedHashMap<>();
    /** 存活的音符链，键 = 锚点打包长整型；同一锚点重复执行 = 覆盖旧链、重置重播。 */
    private static final Map<Long, ActiveChain> CHAINS = new LinkedHashMap<>();

    /** 「音符盒触发特效」开关与多条模板（位置为相对音符盒方块坐标的偏移）。 */
    private static boolean noteTriggerEffect = false;
    private static final List<TriggerSpec> NOTE_TRIGGERS = new ArrayList<>();

    /** 一条「音符盒触发特效」配置：稳定 id + 模板 + 相对音符盒方块的偏移 + 参数原文。 */
    public record TriggerSpec(String id, EffectDisplayParams template, Vec3 offset, String specText) {
    }

    private enum ActiveKind {
        TEXT,
        EFFECT
    }

    private record ActiveInstance(String id, ActiveKind kind, long expireTick, int exitTicks) {
        boolean isActive(long now) {
            return now < expireTick;
        }
    }

    /**
     * 存活的链。linger < 0 表示常驻（expireTick = Long.MAX_VALUE）。
     * 同时维护「音符盒触发特效」的排程游标：nextNote 为下一个待触发音符下标，nextTick 为其绝对触发 tick。
     */
    private static final class ActiveChain {

        private final MiauParticleEffectsPackets.NoteChainPayload payload;
        private final long expireTick;
        private int nextNote;
        private long nextTick;

        ActiveChain(MiauParticleEffectsPackets.NoteChainPayload payload, long expireTick) {
            this.payload = payload;
            this.expireTick = expireTick;
            this.nextTick = firstNoteTick(payload);
        }

        MiauParticleEffectsPackets.NoteChainPayload payload() {
            return payload;
        }

        boolean isActive(long now) {
            return now < expireTick;
        }

        int noteCount() {
            return payload.notes().size();
        }

        int nextNote() {
            return nextNote;
        }

        long nextTick() {
            return nextTick;
        }

        /** 取当前游标所指音符并把游标推进到下一个（触发时刻与客户端 NoteChainPlayer 的 T(i) 完全一致）。 */
        MiauParticleEffectsPackets.NoteChainPayload.Note consume() {
            List<MiauParticleEffectsPackets.NoteChainPayload.Note> notes = payload.notes();
            MiauParticleEffectsPackets.NoteChainPayload.Note note = notes.get(nextNote);
            nextNote++;
            nextTick += nextNote < notes.size() ? Math.max(1, notes.get(nextNote).delay()) : 0;
            return note;
        }

        /** 跳过已经过去的音符：开启触发特效时避免瞬间补播一大串历史特效。 */
        void skipPast(long now) {
            while (nextNote < payload.notes().size() && nextTick <= now) {
                consume();
            }
        }

        /** 第 0 个音符的绝对触发 tick：从命令方块执行那一刻起算上它的 delay。 */
        private static long firstNoteTick(MiauParticleEffectsPackets.NoteChainPayload payload) {
            List<MiauParticleEffectsPackets.NoteChainPayload.Note> notes = payload.notes();
            if (notes.isEmpty()) {
                return Long.MAX_VALUE;
            }
            return payload.startTick() + Math.max(1, notes.get(0).delay());
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

    // ------------------------------------------------------------------ 音符链

    /** 登记/重置一条链并广播整条时间轴；同一锚点会覆盖旧链，达到“再次触发 = 重播”的效果。 */
    public static void noteChain(MinecraftServer server, long anchorKey,
                                 MiauParticleEffectsPackets.NoteChainPayload payload) {
        long now = server.getTickCount();
        CHAINS.put(anchorKey, new ActiveChain(payload, expiry(now, payload)));
        MiauParticleEffectsPackets.broadcast(server, payload);
    }

    /** 移除全部链，返回被移除的数量。 */
    public static int noteChainClearAll(MinecraftServer server) {
        int removed = CHAINS.size();
        CHAINS.clear();
        if (server != null) {
            MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.NoteChainClearPayload(null));
        }
        return removed;
    }

    /** 移除指定 id 的链；不存在时返回 false。 */
    public static boolean noteChainClear(MinecraftServer server, String id) {
        boolean removed = CHAINS.values().removeIf(chain -> chain.payload().id().equals(id));
        if (removed && server != null) {
            MiauParticleEffectsPackets.broadcast(server, new MiauParticleEffectsPackets.NoteChainClearPayload(id));
        }
        return removed;
    }

    /** 当前存活的链（先剔除已自然结束的），供 /mpe noteblock status 使用。 */
    public static List<MiauParticleEffectsPackets.NoteChainPayload> noteChains(MinecraftServer server) {
        pruneChains(server);
        List<MiauParticleEffectsPackets.NoteChainPayload> out = new ArrayList<>(CHAINS.size());
        for (ActiveChain chain : CHAINS.values()) {
            out.add(chain.payload());
        }
        return out;
    }

    /** 剔除已自然结束的链，避免同一结构反复触发不同锚点时映射无限增长。 */
    public static void pruneChains(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long now = server.getTickCount();
        CHAINS.values().removeIf(chain -> !chain.isActive(now));
    }

    /** 锚点自动取色：按黄金比例散开色相，避免多条链颜色相近难以区分。 */
    public static int autoChainColor(long anchorKey) {
        float hue = (float) (((anchorKey & 0x7FFFFFFFL) * 0.61803398875) % 1.0);
        return ColorCodec.hsvToRgb(hue);
    }

    // ------------------------------------------------------------------ 歌词链

    /** 存活的歌词链，键 = 链 id；同一 id 重复执行 = 覆盖旧链、从头重播。 */
    private static final Map<String, ActiveTextChain> TEXT_CHAINS = new LinkedHashMap<>();

    private record ActiveTextChain(MiauParticleEffectsPackets.TextChainPayload payload, long expireTick) {
        boolean isActive(long now) {
            return now < expireTick;
        }
    }

    /** 登记/重置一条歌词链并广播整条时间轴。 */
    public static void textChain(MinecraftServer server, MiauParticleEffectsPackets.TextChainPayload payload) {
        long now = server.getTickCount();
        long total = 0;
        for (MiauParticleEffectsPackets.TextChainPayload.Entry entry : payload.entries()) {
            long end = (long) entry.startTick() + entry.params().totalTicks();
            if (end > total) {
                total = end;
            }
        }
        long expire = payload.linger() < 0 ? Long.MAX_VALUE : now + total + payload.linger();
        TEXT_CHAINS.put(payload.id(), new ActiveTextChain(payload, expire));
        MiauParticleEffectsPackets.broadcast(server, payload);
    }

    public static int textChainClearAll(MinecraftServer server) {
        int removed = TEXT_CHAINS.size();
        TEXT_CHAINS.clear();
        if (server != null) {
            MiauParticleEffectsPackets.broadcast(server,
                    new MiauParticleEffectsPackets.TextChainClearPayload(null));
        }
        return removed;
    }

    public static boolean textChainClear(MinecraftServer server, String id) {
        boolean removed = TEXT_CHAINS.remove(id) != null;
        if (removed && server != null) {
            MiauParticleEffectsPackets.broadcast(server,
                    new MiauParticleEffectsPackets.TextChainClearPayload(id));
        }
        return removed;
    }

    /** 当前存活的歌词链（先剔除已自然结束的）。 */
    public static List<MiauParticleEffectsPackets.TextChainPayload> textChains(MinecraftServer server) {
        if (server != null) {
            long now = server.getTickCount();
            TEXT_CHAINS.values().removeIf(chain -> !chain.isActive(now));
        }
        List<MiauParticleEffectsPackets.TextChainPayload> out = new ArrayList<>(TEXT_CHAINS.size());
        for (ActiveTextChain chain : TEXT_CHAINS.values()) {
            out.add(chain.payload());
        }
        return out;
    }

    // ------------------------------------------------------------------ 音符盒触发特效

    /**
     * 服务端逐 tick 排程：与客户端共用的同一条时间轴，到点即在对应音符盒位置广播一次特效。
     * 关闭该功能、无模板或没有存活链时直接返回。
     */
    public static void tick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        pruneChains(server);
        if (!TEXT_CHAINS.isEmpty()) {
            long now = server.getTickCount();
            TEXT_CHAINS.values().removeIf(chain -> !chain.isActive(now));
        }
        // 每 tick 广播存活链的进度：客户端据此平滑跟随。
        // 这样 /tick rate 变速、暂停、/tick freeze、回放都与服务端（音乐）严格对齐。
        broadcastChainProgress(server);
        if (!noteTriggerEffect || NOTE_TRIGGERS.isEmpty() || CHAINS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        for (ActiveChain chain : CHAINS.values()) {
            // while 而非 if：服务端卡顿导致错过多个时刻时补触发，保证每个音符都响一次。
            while (chain.nextNote() < chain.noteCount() && chain.nextTick() <= now) {
                fireNoteTrigger(server, chain);
            }
        }
    }

    /**
     * 广播存活链的服务端进度（id + 已播放 tick 数）。
     *
     * <p>服务端 tick 频率就是 `{@code /tick rate}` 的权威值，所以客户端收到的进度天然跟随变速；
     * 暂停 / {@code /tick freeze} 时服务端不 tick，进度不再变化，客户端会自动冻结；
     * 回放时这些包被重放，进度与录制的音乐严格一致。</p>
     */
    private static void broadcastChainProgress(MinecraftServer server) {
        if (CHAINS.isEmpty() && TEXT_CHAINS.isEmpty()) {
            return;
        }
        long now = server.getTickCount();
        List<MiauParticleEffectsPackets.NoteChainProgressPayload.ProgressEntry> entries =
                new ArrayList<>(CHAINS.size() + TEXT_CHAINS.size());
        for (ActiveChain chain : CHAINS.values()) {
            long elapsed = now - chain.payload().startTick();
            entries.add(new MiauParticleEffectsPackets.NoteChainProgressPayload.ProgressEntry(
                    chain.payload().id(), (float) Math.max(0, elapsed)));
        }
        for (ActiveTextChain chain : TEXT_CHAINS.values()) {
            long elapsed = now - chain.payload().startTick();
            entries.add(new MiauParticleEffectsPackets.NoteChainProgressPayload.ProgressEntry(
                    chain.payload().id(), (float) Math.max(0, elapsed)));
        }
        MiauParticleEffectsPackets.broadcast(server,
                new MiauParticleEffectsPackets.NoteChainProgressPayload(entries));
    }

    private static void fireNoteTrigger(MinecraftServer server, ActiveChain chain) {        MiauParticleEffectsPackets.NoteChainPayload payload = chain.payload();
        MiauParticleEffectsPackets.NoteChainPayload.Note note = chain.consume();
        int index = chain.nextNote() - 1;
        // 音符盒所在方块坐标，再叠加各条配置各自的相对偏移（~~~ = 该音符盒方块）。
        Vec3 base = new Vec3(
                payload.ax() + note.ox(),
                payload.ay() + note.oy(),
                payload.az() + note.oz());
        int triggerIndex = 0;
        for (TriggerSpec trigger : NOTE_TRIGGERS) {
            Vec3 at = base.add(trigger.offset());
            MiauParticleEffectsPackets.broadcast(server,
                    new MiauParticleEffectsPackets.ShowEffectPayload(
                            trigger.template().withPos(at,
                                    "nbtrigger-" + payload.id() + "-" + index + "-" + triggerIndex)));
            triggerIndex++;
        }
    }

    /** 开关「音符盒触发特效」；开启时跳过已经过去的音符，避免补播历史特效。 */
    public static void setNoteTriggerEffect(MinecraftServer server, boolean enabled) {
        noteTriggerEffect = enabled;
        if (enabled && server != null) {
            long now = server.getTickCount();
            for (ActiveChain chain : CHAINS.values()) {
                chain.skipPast(now);
            }
        }
    }

    public static boolean noteTriggerEffectEnabled() {
        return noteTriggerEffect;
    }

    public static void setNoteTriggerSpecs(List<TriggerSpec> specs) {
        NOTE_TRIGGERS.clear();
        NOTE_TRIGGERS.addAll(specs);
    }

    public static List<TriggerSpec> noteTriggerSpecs() {
        return List.copyOf(NOTE_TRIGGERS);
    }

    /** 添加一条配置；id 已存在时覆盖。 */
    public static void addNoteTriggerSpec(TriggerSpec spec) {
        NOTE_TRIGGERS.removeIf(existing -> existing.id().equals(spec.id()));
        NOTE_TRIGGERS.add(spec);
    }

    public static boolean removeNoteTriggerSpec(String id) {
        return NOTE_TRIGGERS.removeIf(spec -> spec.id().equals(id));
    }

    public static void clearNoteTriggerSpecs() {
        NOTE_TRIGGERS.clear();
    }

    public static boolean hasNoteTriggerSpec() {
        return !NOTE_TRIGGERS.isEmpty();
    }

    /** 服务器停止时清空所有与「服务器 tick 计数」相关的静态状态，避免跨存档沿用旧时刻。 */
    public static void reset() {
        ACTIVE.clear();
        CHAINS.clear();
        TEXT_CHAINS.clear();
    }

    /** 计算链的到期 tick：最后一个音符结束后再停留 linger tick。 */
    private static long expiry(long now, MiauParticleEffectsPackets.NoteChainPayload payload) {
        if (payload.linger() < 0) {
            return Long.MAX_VALUE;
        }
        long total = payload.linger();
        for (MiauParticleEffectsPackets.NoteChainPayload.Note note : payload.notes()) {
            total += Math.max(1, note.delay());
        }
        return now + total;
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
