package org.miau.particleeffects.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import org.miau.particleeffects.client.display.ClientDisplayBackend;
import org.miau.particleeffects.client.display.ClientDisplayManager;
import org.miau.particleeffects.client.particle.ColoredEndRodParticle;
import org.miau.particleeffects.client.render.FrameDriver;
import org.miau.particleeffects.particle.MiauParticleEffectsParticles;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;

public final class MiauParticleEffectsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientDisplayBackend.init();
        // 把特效与弹力球的位置推进提升到渲染帧率（约 60FPS）
        FrameDriver.init();

        ParticleProviderRegistry.getInstance().register(
                MiauParticleEffectsParticles.COLORED_END_ROD,
                ColoredEndRodParticle::createFactory);
        ParticleProviderRegistry.getInstance().register(
                MiauParticleEffectsParticles.COLORED_HEAD,
                ColoredEndRodParticle::createFactory);

        ClientTickEvents.END_CLIENT_TICK.register(client ->
                ClientDisplayBackend.instance().tick());

        ClientPlayNetworking.registerGlobalReceiver(
                MiauParticleEffectsPackets.ShowTextPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> ClientDisplayManager.showText(payload.options())));

        ClientPlayNetworking.registerGlobalReceiver(
                MiauParticleEffectsPackets.ShowEffectPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> ClientDisplayManager.showEffect(payload.options())));

        ClientPlayNetworking.registerGlobalReceiver(
                MiauParticleEffectsPackets.ClearPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> ClientDisplayManager.clear(payload.scope(), payload.id())));

        ClientPlayNetworking.registerGlobalReceiver(
                MiauParticleEffectsPackets.NoteChainPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> ClientDisplayManager.noteChain(payload)));

        ClientPlayNetworking.registerGlobalReceiver(
                MiauParticleEffectsPackets.NoteChainClearPayload.ID,
                (payload, context) -> context.client().execute(
                        () -> ClientDisplayManager.noteChainClear(payload.id())));
    }
}