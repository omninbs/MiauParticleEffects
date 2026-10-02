package org.miau.particleeffects;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.miau.particleeffects.command.MiauParticleEffectsCommand;
import org.miau.particleeffects.config.ConfigManager;
import org.miau.particleeffects.particle.MiauParticleEffectsParticles;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;
import org.miau.particleeffects.server.NoteTrackManager;

public final class MiauParticleEffects implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigManager.init();
        MiauParticleEffectsParticles.init();
        MiauParticleEffectsPackets.registerCommon();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MiauParticleEffectsCommand.register(dispatcher));

        // 轨道选择模式：拦截玩家破坏方块，记录为轨道种子且不真正破坏方块。
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(world instanceof ServerLevel serverWorld && player instanceof ServerPlayer serverPlayer)
                        || NoteTrackManager.onBreakAttempt(serverWorld, serverPlayer, pos));
    }
}
