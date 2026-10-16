package org.miau.particleeffects.client.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.miau.particleeffects.client.noteblock.NoteChainPlayer;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.NoteChainPayload;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 客户端显示后端：持有全部显示实例与音符链播放器，在 tick 用末地烛粒子生成显示内容。
 * 作为 {@link ClientDisplayManager.Backend} 被注入。 */
public final class ClientDisplayBackend implements ClientDisplayManager.Backend {

    private static final ClientDisplayBackend INSTANCE = new ClientDisplayBackend();

    private final List<DisplayInstance> instances = new ArrayList<>();
    /** 存活的音符链：每条链一颗弹力球，各自独立按时间轴播放。 */
    private final List<NoteChainPlayer> noteChains = new ArrayList<>();
    private ClientLevel lastWorld;

    private ClientDisplayBackend() {
    }

    public static ClientDisplayBackend instance() {
        return INSTANCE;
    }

    public static void init() {
        ClientDisplayManager.setBackend(INSTANCE);
    }

    @Override
    public void showText(TextDisplayParams params) {
        removeById(params.id());
        instances.add(new TextInstance(params));
    }

    @Override
    public void showEffect(EffectDisplayParams params) {
        removeById(params.id());
        instances.add(new EffectInstance(params));
    }

    @Override
    public void clear(ClearScope scope, String id) {
        Iterator<DisplayInstance> it = instances.iterator();
        while (it.hasNext()) {
            DisplayInstance inst = it.next();
            boolean match = switch (scope) {
                case ALL -> true;
                case TEXT -> inst instanceof TextInstance;
                case EFFECT -> inst instanceof EffectInstance;
            };
            if (id != null) {
                match = inst.id() != null && inst.id().equals(id);
            }
            if (match) {
                inst.forceExit();
                if (inst.isDeleted()) {
                    it.remove();
                }
            }
        }
    }

    private void removeById(String id) {
        if (id == null) {
            return;
        }
        instances.removeIf(inst -> id.equals(inst.id()));
    }

    @Override
    public void noteChain(NoteChainPayload payload) {
        // 同一锚点（同 id）再次触发 = 重置该链：先移除旧链，再以新时间轴从锚点重播。
        noteChains.removeIf(chain -> chain.id().equals(payload.id()));
        noteChains.add(new NoteChainPlayer(payload));
    }

    @Override
    public void noteChainClear(String id) {
        if (id == null) {
            noteChains.clear();
        } else {
            noteChains.removeIf(chain -> chain.id().equals(id));
        }
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return;
        }
        ClientLevel world = client.level;
        if (world == null) {
            lastWorld = null;
            return;
        }
        if (lastWorld != world) {
            // 切换维度/世界：旧世界的粒子已随世界卸载消失，显示实例与弹力球一并清空，避免在新世界残留旧坐标的“残影”。
            lastWorld = world;
            instances.clear();
            noteChains.clear();
        }
        Iterator<DisplayInstance> it = instances.iterator();
        while (it.hasNext()) {
            DisplayInstance inst = it.next();
            inst.tick();
            // 帧驱动（60FPS）的实例改由渲染帧回调生成粒子，此处跳过以免重复撒点。
            if (!inst.frameDriven()) {
                inst.spawnParticles(world, 0f);
            }
            if (inst.isDeleted()) {
                it.remove();
            }
        }
        // 弹力球只在这里推进时间轴；粒子由 frame() 按渲染帧生成。
        Iterator<NoteChainPlayer> chainIt = noteChains.iterator();
        while (chainIt.hasNext()) {
            NoteChainPlayer chain = chainIt.next();
            chain.tick(world);
            if (chain.isRemoved()) {
                chainIt.remove();
            }
        }
    }

    /**
     * 渲染帧驱动（约 60FPS）：用帧间插值系数重新求位置并生成粒子。
     * 原版粒子只在 20Hz 的 tick 上更新位置，渲染时仅做两 tick 之间的直线插值，
     * 高速运动会出现明显折线感；这里按帧求值即可得到连续曲线。
     *
     * @param partialTick 自上一个客户端 tick 起经过的比例（0~1）
     */
    public void frame(ClientLevel world, float partialTick) {
        for (DisplayInstance inst : instances) {
            if (inst.frameDriven()) {
                inst.spawnParticles(world, partialTick);
            }
        }
        for (NoteChainPlayer chain : noteChains) {
            chain.frame(world, partialTick);
        }
    }
}
