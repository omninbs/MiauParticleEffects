package org.miau.particleeffects.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.noteblock.NoteBlockListener;
import org.miau.particleeffects.noteblock.NoteTrack;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 音符盒弹力球运行时（按轨道）。 *
 * 一颗弹力球对应一条已选轨道。音符盒被激活时，按其所属轨道直接驱动该轨道的弹力球：
 * 同一 tick 内本轨若有多個音符同时触发，随机取其中一个；因此不存在“球被分到别的轨道
 * 后卡住不动”的情况。 *
 * 节奏同步：每条轨道各有一个 {@link NoteChainPredictor}，学到“本轨内 A 的下一个音符盒 B
 * 以及间隔”后，在 A 激活当下就把 B 连同提前量一起下发，让弹力球提前起飞、准时落到 B 上。
 */
public final class NoteBlockRuntime implements NoteBlockListener {

    /** 单次跳跃时长上限，与客户端保持一致。 */
    private static final int MAX_LEAD_TICKS = 60;

    private final NoteBlockParams config;
    private final List<NoteTrack> tracks;
    private final NoteChainPredictor[] predictors;
    private final Vec3[] lastTargets;
    private final Map<Integer, List<Pending>> batch = new HashMap<>();
    private final Random random = new Random();

    private MinecraftServer server;
    private long batchTick = -1;

    private record Pending(BlockPos pos, Vec3 target, int note) {
    }

    public NoteBlockRuntime(NoteBlockParams config, List<NoteTrack> tracks) {
        this.config = config;
        this.tracks = List.copyOf(tracks);
        this.predictors = new NoteChainPredictor[this.tracks.size()];
        this.lastTargets = new Vec3[this.tracks.size()];
        for (int i = 0; i < this.tracks.size(); i++) {
            predictors[i] = new NoteChainPredictor();
        }
    }

    public void bindServer(MinecraftServer server) {
        this.server = server;
    }

    public NoteBlockParams config() {
        return config;
    }

    public int trackCount() {
        return tracks.size();
    }

    @Override
    public void onNoteBlock(ServerLevel world, BlockPos pos, int pitch) {
        if (server == null) {
            return;
        }
        Integer trackIndex = NoteTrackManager.trackOf(pos);
        if (trackIndex == null || trackIndex < 0 || trackIndex >= tracks.size()) {
            return;
        }
        Vec3 target = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        Vec3 center = config.center();
        if (Math.hypot(target.x - center.x, target.z - center.z) > config.radius()) {
            return;
        }
        long tick = server.getTickCount();
        if (tick != batchTick) {
            // 新的一 tick：先把上一 tick 收集的激活批量下发，再重新开始收集。
            flush();
            batchTick = tick;
        }
        batch.computeIfAbsent(trackIndex, k -> new ArrayList<>())
                .add(new Pending(pos.immutable(), target, pitch));
    }

    /**
     * 服务端 tick 末调用，把本 tick 收集的激活按轨道下发。
     */
    public void tick(MinecraftServer server) {
        if (this.server != null && this.server != server) {
            return;
        }
        flush();
        batchTick = -1;
    }

    private void flush() {
        if (batch.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, List<Pending>> entry : batch.entrySet()) {
            int trackIndex = entry.getKey();
            if (trackIndex < 0 || trackIndex >= tracks.size()) {
                continue;
            }
            List<Pending> list = entry.getValue();
            // 同一条轨道同一 tick 内多个音符同时触发：随机取其中一个。
            Pending chosen = list.get(random.nextInt(list.size()));
            NoteChainPredictor predictor = predictors[trackIndex];
            for (Pending p : list) {
                predictor.observe(p.pos(), batchTick);
            }

            if (!chosen.target().equals(lastTargets[trackIndex])) {
                send(trackIndex, chosen.target(), chosen.note(), 0);
                lastTargets[trackIndex] = chosen.target();
            }

            // 轨内预测下一个音符盒：提前下发，让球正好在它发声时落到其上。
            NoteChainPredictor.Prediction pred = predictor.predict(chosen.pos());
            if (pred == null) {
                continue;
            }
            Vec3 nextTarget = new Vec3(
                    pred.pos().getX() + 0.5, pred.pos().getY() + 1.0, pred.pos().getZ() + 0.5);
            if (!nextTarget.equals(chosen.target()) && !nextTarget.equals(lastTargets[trackIndex])) {
                send(trackIndex, nextTarget, 0, Math.min(MAX_LEAD_TICKS, pred.leadTicks()));
                lastTargets[trackIndex] = nextTarget;
            }
        }
        batch.clear();
    }

    private void send(int trackIndex, Vec3 target, int note, int durationTicks) {
        if (server == null) {
            return;
        }
        MiauParticleEffectsPackets.broadcast(server,
                new MiauParticleEffectsPackets.NoteBlockActivationPayload(trackIndex, target, note, durationTicks));
    }
}
