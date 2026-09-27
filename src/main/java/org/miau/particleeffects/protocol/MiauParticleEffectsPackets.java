package org.miau.particleeffects.protocol;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.HorizontalCurve;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.server.MiauParticleEffectsServer;

public final class MiauParticleEffectsPackets {

    private MiauParticleEffectsPackets() {
    }

    public static void registerCommon() {
        PayloadTypeRegistry.playS2C().register(ShowTextPayload.ID, ShowTextPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowEffectPayload.ID, ShowEffectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ClearPayload.ID, ClearPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NoteBlockStartPayload.ID, NoteBlockStartPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NoteBlockStopPayload.ID, NoteBlockStopPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(NoteBlockActivationPayload.ID, NoteBlockActivationPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BallLandedC2SPayload.ID, BallLandedC2SPayload.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(BallLandedC2SPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            if (player == null) {
                return;
            }
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server == null) {
                return;
            }
            server.execute(() -> MiauParticleEffectsServer.onBallLanded(payload.ballIndex()));
        });
    }

    public static void broadcast(MinecraftServer server, CustomPayload payload) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    public record ShowTextPayload(TextDisplayParams options) implements CustomPayload {

        public static final CustomPayload.Id<ShowTextPayload> ID =
                new CustomPayload.Id<>(Identifier.of("miauparticleeffects", "show_text"));

        public static final PacketCodec<PacketByteBuf, ShowTextPayload> CODEC = new PacketCodec<>() {
            @Override
            public ShowTextPayload decode(PacketByteBuf buf) {
                return new ShowTextPayload(PayloadCodecs.TEXT.decode(buf));
            }

            @Override
            public void encode(PacketByteBuf buf, ShowTextPayload value) {
                PayloadCodecs.TEXT.encode(buf, value.options());
            }
        };

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ShowEffectPayload(EffectDisplayParams options) implements CustomPayload {

        public static final CustomPayload.Id<ShowEffectPayload> ID =
                new CustomPayload.Id<>(Identifier.of("miauparticleeffects", "show_effect"));

        public static final PacketCodec<PacketByteBuf, ShowEffectPayload> CODEC = new PacketCodec<>() {
            @Override
            public ShowEffectPayload decode(PacketByteBuf buf) {
                return new ShowEffectPayload(PayloadCodecs.EFFECT.decode(buf));
            }

            @Override
            public void encode(PacketByteBuf buf, ShowEffectPayload value) {
                PayloadCodecs.EFFECT.encode(buf, value.options());
            }
        };

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ClearPayload(ClearScope scope, String id) implements CustomPayload {

        public static final CustomPayload.Id<ClearPayload> ID =
                new CustomPayload.Id<>(Identifier.of("miauparticleeffects", "clear"));

        public static final PacketCodec<PacketByteBuf, ClearPayload> CODEC = new PacketCodec<>() {
            @Override
            public ClearPayload decode(PacketByteBuf buf) {
                ClearScope scope = ClearScope.parse(buf.readString(32));
                String id = buf.readBoolean() ? buf.readString(128) : null;
                return new ClearPayload(scope != null ? scope : ClearScope.ALL, id);
            }

            @Override
            public void encode(PacketByteBuf buf, ClearPayload value) {
                buf.writeString(value.scope().id());
                buf.writeBoolean(value.id() != null);
                if (value.id() != null) {
                    buf.writeString(value.id());
                }
            }
        };

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record NoteBlockStartPayload(NoteBlockParams options) implements CustomPayload {

        public static final CustomPayload.Id<NoteBlockStartPayload> ID =
                new CustomPayload.Id<>(Identifier.of("miauparticleeffects", "noteblock_start"));

        public static final PacketCodec<PacketByteBuf, NoteBlockStartPayload> CODEC = new PacketCodec<>() {
            @Override
            public NoteBlockStartPayload decode(PacketByteBuf buf) {
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float radius = buf.readFloat();
                int trail = buf.readVarInt();
                String curveName = buf.readString(32);
                int balls = buf.readVarInt();
                float height = buf.readFloat();
                boolean force = buf.readBoolean();
                HorizontalCurve curve = curveName.isEmpty() ? HorizontalCurve.ARC : parseCurve(curveName);
                try {
                    return new NoteBlockStartPayload(new NoteBlockParams(
                            new Vec3d(x, y, z), radius, trail, curve, balls, height, force));
                } catch (RuntimeException e) {
                    return new NoteBlockStartPayload(new NoteBlockParams(
                            new Vec3d(x, y, z), NoteBlockParams.DEFAULT_RADIUS,
                            NoteBlockParams.DEFAULT_TRAIL, NoteBlockParams.DEFAULT_CURVE,
                            NoteBlockParams.DEFAULT_BALLS, NoteBlockParams.DEFAULT_HEIGHT, false));
                }
            }

            @Override
            public void encode(PacketByteBuf buf, NoteBlockStartPayload value) {
                buf.writeDouble(value.options().center().x);
                buf.writeDouble(value.options().center().y);
                buf.writeDouble(value.options().center().z);
                buf.writeFloat(value.options().radius());
                buf.writeVarInt(value.options().trailCount());
                buf.writeString(value.options().curve().id());
                buf.writeVarInt(value.options().ballCount());
                buf.writeFloat(value.options().maxJumpHeight());
                buf.writeBoolean(value.options().force());
            }

            private static HorizontalCurve parseCurve(String s) {
                HorizontalCurve c = HorizontalCurve.parse(s);
                return c != null ? c : HorizontalCurve.ARC;
            }
        };

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record NoteBlockStopPayload() implements CustomPayload {

        public static final CustomPayload.Id<NoteBlockStopPayload> ID =
                new CustomPayload.Id<>(Identifier.of("miauparticleeffects", "noteblock_stop"));

        public static final PacketCodec<PacketByteBuf, NoteBlockStopPayload> CODEC = new PacketCodec<>() {
            @Override
            public NoteBlockStopPayload decode(PacketByteBuf buf) {
                return new NoteBlockStopPayload();
            }

            @Override
            public void encode(PacketByteBuf buf, NoteBlockStopPayload value) {
            }
        };

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record NoteBlockActivationPayload(int ballIndex, Vec3d target, int note) implements CustomPayload {

        public static final CustomPayload.Id<NoteBlockActivationPayload> ID =
                new CustomPayload.Id<>(Identifier.of("miauparticleeffects", "noteblock_activation"));

        public static final PacketCodec<PacketByteBuf, NoteBlockActivationPayload> CODEC = new PacketCodec<>() {
            @Override
            public NoteBlockActivationPayload decode(PacketByteBuf buf) {
                return new NoteBlockActivationPayload(
                        buf.readVarInt(),
                        new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readVarInt());
            }

            @Override
            public void encode(PacketByteBuf buf, NoteBlockActivationPayload value) {
                buf.writeVarInt(value.ballIndex());
                buf.writeDouble(value.target().x);
                buf.writeDouble(value.target().y);
                buf.writeDouble(value.target().z);
                buf.writeVarInt(value.note());
            }
        };

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record BallLandedC2SPayload(int ballIndex) implements CustomPayload {

        public static final CustomPayload.Id<BallLandedC2SPayload> ID =
                new CustomPayload.Id<>(Identifier.of("miauparticleeffects", "ball_landed"));

        public static final PacketCodec<PacketByteBuf, BallLandedC2SPayload> CODEC = new PacketCodec<>() {
            @Override
            public BallLandedC2SPayload decode(PacketByteBuf buf) {
                return new BallLandedC2SPayload(buf.readVarInt());
            }

            @Override
            public void encode(PacketByteBuf buf, BallLandedC2SPayload value) {
                buf.writeVarInt(value.ballIndex());
            }
        };

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
