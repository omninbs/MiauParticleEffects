package org.miau.particleeffects;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.miau.particleeffects.command.MiauParticleEffectsCommand;
import org.miau.particleeffects.config.ConfigManager;
import org.miau.particleeffects.config.MiauParticleEffectsConfig;
import org.miau.particleeffects.particle.MiauParticleEffectsParticles;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;
import org.miau.particleeffects.server.MiauParticleEffectsServer;

public final class MiauParticleEffects implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigManager.init();
        // 恢复上次的「音符盒触发特效」参数与开关（初始化期还没有服务器实例，传 null）。
        MiauParticleEffectsConfig config = ConfigManager.get();
        MiauParticleEffectsCommand.loadTriggerSpec(config.noteTriggerEffectSpec);
        MiauParticleEffectsServer.setNoteTriggerEffect(null, config.noteTriggerEffect);
        MiauParticleEffectsParticles.init();
        MiauParticleEffectsPackets.registerCommon();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MiauParticleEffectsCommand.register(dispatcher));
        // 「音符盒触发特效」按时间轴逐 tick 排程
        ServerTickEvents.END_SERVER_TICK.register(MiauParticleEffectsServer::tick);
    }
}
