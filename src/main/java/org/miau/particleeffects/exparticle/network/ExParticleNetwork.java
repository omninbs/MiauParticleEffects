package org.miau.particleeffects.exparticle.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * 服务端发送 ExParticle 载荷的入口：把各功能按原读写顺序写进缓冲区，再整包发给在线玩家。
 */
public final class ExParticleNetwork {

    private ExParticleNetwork() {
    }

    public static void send(ServerLevel level, String channel, Consumer<FriendlyByteBuf> writer) {
        if (level == null || level.getServer() == null) {
            return;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        writer.accept(buf);
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        ExParticlePayload payload = new ExParticlePayload(channel, data);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
