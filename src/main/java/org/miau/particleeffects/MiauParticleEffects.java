package org.miau.particleeffects;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.miau.particleeffects.command.MiauParticleEffectsCommand;
import org.miau.particleeffects.config.ConfigManager;
import org.miau.particleeffects.particle.MiauParticleEffectsParticles;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;

public final class MiauParticleEffects implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigManager.init();
        MiauParticleEffectsParticles.init();
        MiauParticleEffectsPackets.registerCommon();
        MiauParticleEffectsPackets.registerServerReceivers();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MiauParticleEffectsCommand.register(dispatcher));
    }
}