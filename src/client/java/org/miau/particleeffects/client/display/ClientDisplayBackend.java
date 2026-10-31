package org.miau.particleeffects.client.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.miau.particleeffects.client.noteblock.NoteChainPlayer;
import org.miau.particleeffects.client.text.TextChainPlayer;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.NoteChainPayload;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.TextChainPayload;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 客户端显示后端：持有全部显示实例、音符链与歌词链播放器，在 tick / 渲染帧生成粒子。
 * 作为 {@link ClientDisplayManager.Backend} 被注入。 */
public final class ClientDisplayBackend implements ClientDisplayManager.Backend {

    private static final ClientDisplayBackend INSTANCE = new ClientDisplayBackend();

    private final List<DisplayInstance> instances = new ArrayList<>();
    /** 存活的音符链：每条链一颗弹力球，各自独立按时间轴播放。 */
    private final List<NoteChainPlayer> noteChains = new ArrayList<>();
    /** 存活的歌词链：每条链一套时间轴，到点才创建对应的文字实例。 */
    private final List<TextChainPlayer> textChains = new ArrayList<>();
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

    /** 歌词链到点触发的文字：与 {@link #showText} 相同，但语义上不参与 autoclear。 */
    public void showChainText(TextDisplayParams params) {
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
        if (id == null) {
            // 全清/按类型清理时，时间轴也要停掉，否则下一段歌词还会照常出现。
            if (scope == ClearScope.ALL) {
                textChains.clear();
                noteChains.clear();
            } else if (scope == ClearScope.TEXT) {
                textChains.clear();
            }
        }
    }

    /** 按 id 前缀清除（歌词链清理自己的所有实例）。 */
    public void clearByPrefix(String prefix) {
        Iterator<DisplayInstance> it = instances.iterator();
        while (it.hasNext()) {
            DisplayInstance inst = it.next();
            if (inst.id() != null && inst.id().startsWith(prefix)) {
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

    @Override
    public void textChain(TextChainPayload payload) {
        // 同一 id 再次触发 = 重置该链：清掉旧链已显示的文字，从头开始播。
        for (TextChainPlayer chain : List.copyOf(textChains)) {
            if (chain.id().equals(payload.id())) {
                chain.clearInstances();
            }
        }
        textChains.removeIf(chain -> chain.id().equals(payload.id()));
        textChains.add(new TextChainPlayer(payload));
    }

    @Override
    public void textChainClear(String id) {
        if (id == null) {
            for (TextChainPlayer chain : textChains) {
                chain.clearInstances();
            }
            textChains.clear();
            return;
        }
        for (TextChainPlayer chain : List.copyOf(textChains)) {
            if (chain.id().equals(id)) {
                chain.clearInstances();
            }
        }
        textChains.removeIf(chain -> chain.id().equals(id));
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
            textChains.clear();
        }
        Iterator<DisplayInstance> it = instances.iterator();
        while (it.hasNext()) {
            DisplayInstance inst = it.next();
            inst.tick();
            // 帧驱动（约 60FPS）的实例改由渲染帧回调生成粒子，此处跳过以免重复撒点。
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
        // 歌词链：到点就创建文字实例，没到点什么都不做。
        Iterator<TextChainPlayer> textIt = textChains.iterator();
        while (textIt.hasNext()) {
            TextChainPlayer chain = textIt.next();
            chain.tick();
            if (chain.isRemoved()) {
                textIt.remove();
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
        for (int i = 0; i < instances.size(); i++) {
            DisplayInstance inst = instances.get(i);
            if (inst.frameDriven()) {
                inst.spawnParticles(world, partialTick);
            }
        }
        for (int i = 0; i < noteChains.size(); i++) {
            noteChains.get(i).frame(world, partialTick);
        }
    }
}
