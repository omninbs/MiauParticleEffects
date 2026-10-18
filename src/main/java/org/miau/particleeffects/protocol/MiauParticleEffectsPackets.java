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

}
