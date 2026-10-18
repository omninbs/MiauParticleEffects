package org.miau.particleeffects.exparticle.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * ExParticle 部分使用的通用网络载荷。
 *
 * <p>原模组按功能拆了 9 个通道，这里统一收敛到一个载荷：{@code channel} 标识具体功能，
 * {@code data} 是按原顺序序列化好的字节。客户端收到后按 channel 分发解包，
 * 这样各功能的读写顺序可以几乎原样保留。</p>
 */
public record ExParticlePayload(String channel, byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExParticlePayload> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("miauparticleeffects", "exparticle"));

    /** 单包数据上限，防止异常数据撑爆内存。 */
    private static final int MAX_DATA = 4 << 20;

    public static final StreamCodec<FriendlyByteBuf, ExParticlePayload> CODEC = new StreamCodec<>() {
        @Override
        public ExParticlePayload decode(FriendlyByteBuf buf) {
            String channel = buf.readUtf(64);
            int length = buf.readVarInt();
            if (length < 0 || length > MAX_DATA) {
                length = 0;
            }
            byte[] data = new byte[length];
            buf.readBytes(data);
            return new ExParticlePayload(channel, data);
        }

        @Override
        public void encode(FriendlyByteBuf buf, ExParticlePayload value) {
            buf.writeUtf(value.channel(), 64);
            buf.writeVarInt(value.data().length);
            buf.writeBytes(value.data());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
