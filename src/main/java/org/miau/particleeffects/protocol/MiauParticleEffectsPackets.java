package org.miau.particleeffects.protocol;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.miau.particleeffects.exparticle.network.ExParticlePayload;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.TextDisplayParams;

import java.util.ArrayList;
import java.util.List;

public final class MiauParticleEffectsPackets {

    private MiauParticleEffectsPackets() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.clientboundPlay().register(ShowTextPayload.ID, ShowTextPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ShowEffectPayload.ID, ShowEffectPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClearPayload.ID, ClearPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NoteChainPayload.ID, NoteChainPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NoteChainClearPayload.ID, NoteChainClearPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NoteChainProgressPayload.ID, NoteChainProgressPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TextChainPayload.ID, TextChainPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TextChainClearPayload.ID, TextChainClearPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExParticlePayload.ID, ExParticlePayload.CODEC);
    }

    public static void broadcast(MinecraftServer server, CustomPacketPayload payload) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public record ShowTextPayload(TextDisplayParams options) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ShowTextPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "show_text"));

        public static final StreamCodec<FriendlyByteBuf, ShowTextPayload> CODEC = new StreamCodec<>() {
            @Override
            public ShowTextPayload decode(FriendlyByteBuf buf) {
                return new ShowTextPayload(PayloadCodecs.TEXT.decode(buf));
            }

            @Override
            public void encode(FriendlyByteBuf buf, ShowTextPayload value) {
                PayloadCodecs.TEXT.encode(buf, value.options());
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ShowEffectPayload(EffectDisplayParams options) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ShowEffectPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "show_effect"));

        public static final StreamCodec<FriendlyByteBuf, ShowEffectPayload> CODEC = new StreamCodec<>() {
            @Override
            public ShowEffectPayload decode(FriendlyByteBuf buf) {
                return new ShowEffectPayload(PayloadCodecs.EFFECT.decode(buf));
            }

            @Override
            public void encode(FriendlyByteBuf buf, ShowEffectPayload value) {
                PayloadCodecs.EFFECT.encode(buf, value.options());
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record ClearPayload(ClearScope scope, String id) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<ClearPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "clear"));

        public static final StreamCodec<FriendlyByteBuf, ClearPayload> CODEC = new StreamCodec<>() {
            @Override
            public ClearPayload decode(FriendlyByteBuf buf) {
                ClearScope scope = ClearScope.parse(buf.readUtf(32));
                String id = buf.readBoolean() ? buf.readUtf(128) : null;
                return new ClearPayload(scope != null ? scope : ClearScope.ALL, id);
            }

            @Override
            public void encode(FriendlyByteBuf buf, ClearPayload value) {
                buf.writeUtf(value.scope().id());
                buf.writeBoolean(value.id() != null);
                if (value.id() != null) {
                    buf.writeUtf(value.id());
                }
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * 一条音符链的完整时间轴：一次性下发整首歌，客户端本地按表播放。
     *
     * 之所以整条下发而不是逐音符发包：时序数据由生成器直接给出，客户端可以完全离线按
     * 已知时刻表推进，既不需要服务端逐音符调度，也不会因网络抖动导致节奏漂移。
     */
    public record NoteChainPayload(
            String id,
            double ax, double ay, double az,
            List<Note> notes,
            float trail,
            String curve,
            float height,
            boolean force,
            int color,
            List<Integer> gradientColors,
            int linger,
            long startTick) implements CustomPacketPayload {

        /** color 为 -1 表示彩虹循环（与 0xRRGGBB 的取值域不冲突）。 */
        public static final int RAINBOW_COLOR = -1;
        /** 单个音符：相对锚点的方块偏移 + 距上一个音符的 tick 间隔。 */
        public record Note(int ox, int oy, int oz, int delay) {
        }

        /** 音符数量上限，防止异常数据把客户端内存吃满。 */
        private static final int MAX_NOTES = 16384;
        private static final int MAX_GRADIENT = 256;

        public static final CustomPacketPayload.Type<NoteChainPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "note_chain"));

        public static final StreamCodec<FriendlyByteBuf, NoteChainPayload> CODEC = new StreamCodec<>() {
            @Override
            public NoteChainPayload decode(FriendlyByteBuf buf) {
                String id = buf.readUtf(128);
                double ax = buf.readDouble();
                double ay = buf.readDouble();
                double az = buf.readDouble();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_NOTES) {
                    count = 0;
                }
                List<Note> notes = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    notes.add(new Note(buf.readInt(), buf.readInt(), buf.readInt(),
                            Math.max(0, buf.readVarInt())));
                }
                float trail = buf.readFloat();
                String curve = buf.readUtf(32);
                float height = buf.readFloat();
                boolean force = buf.readBoolean();
                int color = buf.readInt();
                int gradientCount = buf.readVarInt();
                if (gradientCount < 0 || gradientCount > MAX_GRADIENT) {
                    gradientCount = 0;
                }
                List<Integer> gradient = new ArrayList<>(gradientCount);
                for (int i = 0; i < gradientCount; i++) {
                    gradient.add(buf.readInt());
                }
                int linger = buf.readVarInt();
                long startTick = buf.readLong();
                return new NoteChainPayload(id, ax, ay, az, notes, trail, curve, height, force,
                        color, gradient, linger, startTick);
            }

            @Override
            public void encode(FriendlyByteBuf buf, NoteChainPayload value) {
                buf.writeUtf(value.id());
                buf.writeDouble(value.ax());
                buf.writeDouble(value.ay());
                buf.writeDouble(value.az());
                List<Note> notes = value.notes();
                buf.writeVarInt(notes.size());
                for (Note n : notes) {
                    buf.writeInt(n.ox());
                    buf.writeInt(n.oy());
                    buf.writeInt(n.oz());
                    buf.writeVarInt(Math.max(0, n.delay()));
                }
                buf.writeFloat(value.trail());
                buf.writeUtf(value.curve());
                buf.writeFloat(value.height());
                buf.writeBoolean(value.force());
                buf.writeInt(value.color());
                buf.writeVarInt(value.gradientColors().size());
                for (int c : value.gradientColors()) {
                    buf.writeInt(c);
                }
                buf.writeVarInt(value.linger());
                buf.writeLong(value.startTick());
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 移除链：id 为 null 表示移除全部（/mpe noteblock clear）。 */
    public record NoteChainClearPayload(String id) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<NoteChainClearPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "note_chain_clear"));

        public static final StreamCodec<FriendlyByteBuf, NoteChainClearPayload> CODEC = new StreamCodec<>() {
            @Override
            public NoteChainClearPayload decode(FriendlyByteBuf buf) {
                return new NoteChainClearPayload(buf.readBoolean() ? buf.readUtf(128) : null);
            }

            @Override
            public void encode(FriendlyByteBuf buf, NoteChainClearPayload value) {
                buf.writeBoolean(value.id() != null);
                if (value.id() != null) {
                    buf.writeUtf(value.id());
                }
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * 音符链的**服务端进度**：服务端每个（或每若干个）游戏 tick 广播一次。
     *
     * <p>为什么需要它：客户端 tick 事件的频率并不随 {@code /tick rate} 变化，
     * 客户端本地也拿不到可靠的服务端 tick 时间，所以「按本地 tick 计时」的动画在
     * {@code /tick rate} 改变后仍以 20 tick/s 走。而服务端 tick 频率是权威的
     * （音符盒触发特效就是靠它才正常变速的），因此把链的时间轴交给服务端广播，
     * 客户端只做平滑跟随：</p>
     * <ul>
     *   <li>{@code /tick rate} 变速 → 服务端 tick 变快/变慢 → 进度增长同步变化；</li>
     *   <li>暂停 / {@code /tick freeze} → 服务端不 tick → 进度不再更新 → 客户端冻结；</li>
     *   <li>回放 → 这些包被录制重放 → 与音乐严格对齐。</li>
     * </ul>
     * <p>包体极小（每链 id + 一个 float），服务端 tick 频率 ≤ 100 时可忽略流量。</p>
     */
    public record NoteChainProgressPayload(List<ProgressEntry> entries) implements CustomPacketPayload {

        /** 单条链的进度：id + 已播放的 tick 数（服务端权威值）。 */
        public record ProgressEntry(String id, float elapsed) {
        }

        private static final int MAX_ENTRIES = 256;

        public static final CustomPacketPayload.Type<NoteChainProgressPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "note_chain_progress"));

        public static final StreamCodec<FriendlyByteBuf, NoteChainProgressPayload> CODEC = new StreamCodec<>() {
            @Override
            public NoteChainProgressPayload decode(FriendlyByteBuf buf) {
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    count = 0;
                }
                List<ProgressEntry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    entries.add(new ProgressEntry(buf.readUtf(128), buf.readFloat()));
                }
                return new NoteChainProgressPayload(entries);
            }

            @Override
            public void encode(FriendlyByteBuf buf, NoteChainProgressPayload value) {
                buf.writeVarInt(value.entries().size());
                for (ProgressEntry entry : value.entries()) {
                    buf.writeUtf(entry.id());
                    buf.writeFloat(entry.elapsed());
                }
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /**
     * 一条歌词链：一次性把整首歌「什么时候、在哪里、显示什么」交给客户端。
     *
     * <p>每段自带完整的 {@link TextDisplayParams}（位置已是世界绝对坐标），
     * {@code startTick} 是相对「命令方块执行那一刻」的绝对 tick，客户端到点才创建显示实例，
     * 因此不会一次性栅格化整首歌的文字。</p>
     */
    public record TextChainPayload(String id, List<Entry> entries, int linger, long startTick)
            implements CustomPacketPayload {

        /** 单段歌词：相对链起点的绝对 tick + 完整的文字显示参数。 */
        public record Entry(int startTick, TextDisplayParams params) {
        }

        private static final int MAX_ENTRIES = 4096;

        public static final CustomPacketPayload.Type<TextChainPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "text_chain"));

        public static final StreamCodec<FriendlyByteBuf, TextChainPayload> CODEC = new StreamCodec<>() {
            @Override
            public TextChainPayload decode(FriendlyByteBuf buf) {
                String id = buf.readUtf(128);
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_ENTRIES) {
                    count = 0;
                }
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int startTick = Math.max(0, buf.readVarInt());
                    entries.add(new Entry(startTick, PayloadCodecs.TEXT.decode(buf)));
                }
                int linger = buf.readVarInt();
                long startTick = buf.readLong();
                return new TextChainPayload(id, entries, linger, startTick);
            }

            @Override
            public void encode(FriendlyByteBuf buf, TextChainPayload value) {
                buf.writeUtf(value.id());
                buf.writeVarInt(value.entries().size());
                for (Entry entry : value.entries()) {
                    buf.writeVarInt(Math.max(0, entry.startTick()));
                    PayloadCodecs.TEXT.encode(buf, entry.params());
                }
                buf.writeVarInt(value.linger());
                buf.writeLong(value.startTick());
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** 移除歌词链：id 为 null 表示移除全部。 */
    public record TextChainClearPayload(String id) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<TextChainClearPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "text_chain_clear"));

        public static final StreamCodec<FriendlyByteBuf, TextChainClearPayload> CODEC = new StreamCodec<>() {
            @Override
            public TextChainClearPayload decode(FriendlyByteBuf buf) {
                return new TextChainClearPayload(buf.readBoolean() ? buf.readUtf(128) : null);
            }

            @Override
            public void encode(FriendlyByteBuf buf, TextChainClearPayload value) {
                buf.writeBoolean(value.id() != null);
                if (value.id() != null) {
                    buf.writeUtf(value.id());
                }
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

}
