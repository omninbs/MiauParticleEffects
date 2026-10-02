package org.miau.particleeffects.protocol;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.HorizontalCurve;
import org.miau.particleeffects.model.NoteBlockParams;
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
        PayloadTypeRegistry.clientboundPlay().register(NoteBlockStartPayload.ID, NoteBlockStartPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NoteBlockStopPayload.ID, NoteBlockStopPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(NoteBlockActivationPayload.ID, NoteBlockActivationPayload.CODEC);
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

    public record NoteBlockStartPayload(NoteBlockParams options, List<Vec3> anchors) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<NoteBlockStartPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "noteblock_start"));

        public static final StreamCodec<FriendlyByteBuf, NoteBlockStartPayload> CODEC = new StreamCodec<>() {
            @Override
            public NoteBlockStartPayload decode(FriendlyByteBuf buf) {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float radius = buf.readFloat();
                int trail = buf.readVarInt();
                String curveName = buf.readUtf(32);
                int balls = buf.readVarInt();
                float height = buf.readFloat();
                boolean force = buf.readBoolean();
                int anchorCount = buf.readVarInt();
                List<Vec3> anchors = new ArrayList<>(anchorCount);
                for (int i = 0; i < anchorCount; i++) {
                    anchors.add(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
                }
                HorizontalCurve curve = curveName.isEmpty() ? HorizontalCurve.ARC : parseCurve(curveName);
                try {
                    return new NoteBlockStartPayload(new NoteBlockParams(
                            new Vec3(x, y, z), radius, trail, curve, balls, height, force), anchors);
                } catch (RuntimeException e) {
                    return new NoteBlockStartPayload(new NoteBlockParams(
                            new Vec3(x, y, z), NoteBlockParams.DEFAULT_RADIUS,
                            NoteBlockParams.DEFAULT_TRAIL, NoteBlockParams.DEFAULT_CURVE,
                            NoteBlockParams.DEFAULT_BALLS, NoteBlockParams.DEFAULT_HEIGHT, false), List.of());
                }
            }

            @Override
            public void encode(FriendlyByteBuf buf, NoteBlockStartPayload value) {
                buf.writeDouble(value.options().center().x);
                buf.writeDouble(value.options().center().y);
                buf.writeDouble(value.options().center().z);
                buf.writeFloat(value.options().radius());
                buf.writeVarInt(value.options().trailCount());
                buf.writeUtf(value.options().curve().id());
                buf.writeVarInt(value.options().ballCount());
                buf.writeFloat(value.options().maxJumpHeight());
                buf.writeBoolean(value.options().force());
                List<Vec3> anchors = value.anchors();
                buf.writeVarInt(anchors.size());
                for (Vec3 anchor : anchors) {
                    buf.writeDouble(anchor.x);
                    buf.writeDouble(anchor.y);
                    buf.writeDouble(anchor.z);
                }
            }

            private static HorizontalCurve parseCurve(String s) {
                HorizontalCurve c = HorizontalCurve.parse(s);
                return c != null ? c : HorizontalCurve.ARC;
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record NoteBlockStopPayload() implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<NoteBlockStopPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "noteblock_stop"));

        public static final StreamCodec<FriendlyByteBuf, NoteBlockStopPayload> CODEC = new StreamCodec<>() {
            @Override
            public NoteBlockStopPayload decode(FriendlyByteBuf buf) {
                return new NoteBlockStopPayload();
            }

            @Override
            public void encode(FriendlyByteBuf buf, NoteBlockStopPayload value) {
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    public record NoteBlockActivationPayload(int ballIndex, Vec3 target, int note, int durationTicks)
            implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<NoteBlockActivationPayload> ID =
                new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "noteblock_activation"));

        public static final StreamCodec<FriendlyByteBuf, NoteBlockActivationPayload> CODEC = new StreamCodec<>() {
            @Override
            public NoteBlockActivationPayload decode(FriendlyByteBuf buf) {
                return new NoteBlockActivationPayload(
                        buf.readVarInt(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readVarInt(),
                        buf.readVarInt());
            }

            @Override
            public void encode(FriendlyByteBuf buf, NoteBlockActivationPayload value) {
                buf.writeVarInt(value.ballIndex());
                buf.writeDouble(value.target().x);
                buf.writeDouble(value.target().y);
                buf.writeDouble(value.target().z);
                buf.writeVarInt(value.note());
                buf.writeVarInt(value.durationTicks());
            }
        };

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

}
