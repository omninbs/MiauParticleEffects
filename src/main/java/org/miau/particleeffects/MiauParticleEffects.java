package org.miau.particleeffects;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.miau.particleeffects.command.MiauParticleEffectsCommand;
import org.miau.particleeffects.config.ConfigManager;
import org.miau.particleeffects.config.MiauParticleEffectsConfig;
import org.miau.particleeffects.exparticle.ExParticleConfig;
import org.miau.particleeffects.exparticle.command.argument.ExParticleArgumentTypes;
import org.miau.particleeffects.particle.MiauParticleEffectsParticles;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;
import org.miau.particleeffects.server.MiauParticleEffectsServer;

public final class MiauParticleEffects implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigManager.init();
        ExParticleConfig.init();
        // 恢复上次的「音符盒触发特效」参数与开关（初始化期还没有服务器实例，传 null）。
        MiauParticleEffectsConfig config = ConfigManager.get();
        MiauParticleEffectsCommand.loadTriggerSpecs(config);
        MiauParticleEffectsServer.setNoteTriggerEffect(null, config.noteTriggerEffect);
        MiauParticleEffectsParticles.init();
        MiauParticleEffectsPackets.registerCommon();
        // 必须先登记自定义参数类型，否则服务端同步命令树时会抛异常把玩家踢下线
        ExParticleArgumentTypes.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                MiauParticleEffectsCommand.register(dispatcher));
        // 「音符盒触发特效」按时间轴逐 tick 排程
        ServerTickEvents.END_SERVER_TICK.register(MiauParticleEffectsServer::tick);
        // 服务器停止时清空静态登记表：tickCount 会随新服务器归零，
        // 旧记录若留着，其 expireTick 会「永远在未来」，导致 id 一直被占用、状态查询出现幽灵条目。
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> MiauParticleEffectsServer.reset());
    }
}
