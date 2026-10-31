package org.miau.particleeffects.client.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.miau.particleeffects.client.noteblock.NoteChainPlayer;
import org.miau.particleeffects.client.text.TextChainPlayer;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.TextDisplayParams;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.NoteChainPayload;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.NoteChainProgressPayload;
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
    /** 上一次 tick 时所在的维度；只有维度变化才清空（replay 重建 ClientLevel 不算换世界）。 */
    private ResourceKey<Level> lastDimension;
    /** 同一条链在这段时间内重复下发时视为抖动，直接忽略（单位 tick）。 */
    private static final long REPLAY_DEBOUNCE_TICKS = 2L;

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
        // 同一个 id 在极短时间内重复下发（红石信号抖动、replay 重复投递）时忽略：
        // 否则弹力球会被反复重置回起点，看起来就是「闪烁 / 不稳定 / 球没了」。
        for (int i = 0; i < noteChains.size(); i++) {
            NoteChainPlayer chain = noteChains.get(i);
            if (chain.id().equals(payload.id()) && chain.elapsedTicks() < REPLAY_DEBOUNCE_TICKS) {
                return;
            }
        }
        // 同一锚点（同 id）再次触发 = 重置该链：先移除旧链，再以新时间轴从锚点重播。
        noteChains.removeIf(chain -> chain.id().equals(payload.id()));
        noteChains.add(new NoteChainPlayer(payload));
    }

    @Override
    public void noteChainProgress(NoteChainProgressPayload payload) {
        for (NoteChainProgressPayload.ProgressEntry entry : payload.entries()) {
            boolean matched = false;
            for (int i = 0; i < noteChains.size(); i++) {
                NoteChainPlayer chain = noteChains.get(i);
                if (chain.id().equals(entry.id())) {
                    chain.onServerProgress(entry.elapsed());
                    matched = true;
                    break;
                }
            }
            if (matched) {
                continue;
            }
            for (int i = 0; i < textChains.size(); i++) {
                TextChainPlayer chain = textChains.get(i);
                if (chain.id().equals(entry.id())) {
                    chain.onServerProgress(entry.elapsed());
                    break;
                }
            }
        }
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
            lastDimension = null;
            return;
        }
        // 只在「真的换了维度」时清空。
        // 不能按 ClientLevel 对象变化判断：replay（尤其拖动进度条）会重建 ClientLevel，
        // 但世界还是同一个世界；按对象清空会让正在播放的弹力球/歌词直接消失。
        ResourceKey<Level> dimension = world.dimension();
        if (lastDimension == null) {
            lastDimension = dimension;
        } else if (!lastDimension.equals(dimension)) {
            lastDimension = dimension;
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
        // 弹力球只在这里推进时间轴；粒子由 frame() 按渲染帧生成（帧回调失效时链内部会自己兜底）。
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
            chain.tick(world);
            if (chain.isRemoved()) {
                textIt.remove();
            }
        }
    }

    /**
     * 渲染帧驱动（约 60FPS）：求解位置并生成粒子。
     *
     * @param partialTick 游戏刻内的插值系数（0~1），只给「以 tick 为单位」的离散动画用
     * @param deltaTicks  本帧流逝的游戏时间（tick，含小数），球/歌词链用它做连续推进
     */
    public void frame(ClientLevel world, float partialTick, float deltaTicks) {
        for (int i = 0; i < instances.size(); i++) {
            DisplayInstance inst = instances.get(i);
            if (inst.frameDriven()) {
                inst.spawnParticles(world, partialTick);
            }
        }
        for (int i = 0; i < noteChains.size(); i++) {
            noteChains.get(i).frame(world, deltaTicks);
        }
        for (int i = 0; i < textChains.size(); i++) {
            textChains.get(i).frame(world, deltaTicks);
        }
    }
}
