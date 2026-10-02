package org.miau.particleeffects.client.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.client.noteblock.NoteBlockSimulation;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.model.TextDisplayParams;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 客户端显示后端：持有全部显示实例与弹力球模拟，在。tick 用末地烛粒子生成显示内容。 * 作为 ClientDisplayManager 。Backend 被注入。 */
public final class ClientDisplayBackend implements ClientDisplayManager.Backend {

    private static final ClientDisplayBackend INSTANCE = new ClientDisplayBackend();

    private final List<DisplayInstance> instances = new ArrayList<>();
    private final NoteBlockSimulation noteBlockSimulation = new NoteBlockSimulation();
    private ClientLevel lastWorld;

    private ClientDisplayBackend() {
    }

    public static ClientDisplayBackend instance() {
        return INSTANCE;
    }

    public static void init() {
        ClientDisplayManager.setBackend(INSTANCE);
    }

    public NoteBlockSimulation noteBlockSimulation() {
        return noteBlockSimulation;
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
    public void noteBlockStart(NoteBlockParams params, List<Vec3> anchors) {
        noteBlockSimulation.start(params, anchors);
    }

    @Override
    public void noteBlockStop() {
        noteBlockSimulation.stop();
    }

    @Override
    public void noteBlockActivate(int ballIndex, Vec3 target, int note, int durationTicks) {
        noteBlockSimulation.activate(ballIndex, target, note, durationTicks);
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
            noteBlockSimulation.stop();
        }
        Iterator<DisplayInstance> it = instances.iterator();
        while (it.hasNext()) {
            DisplayInstance inst = it.next();
            inst.tick();
            inst.spawnParticles(world);
            if (inst.isDeleted()) {
                it.remove();
            }
        }
        noteBlockSimulation.tick(world);
    }
}