package org.miau.particleeffects.server;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.miau.particleeffects.model.NoteBlockParams;
import org.miau.particleeffects.noteblock.NoteBlockListener;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets;

import java.util.ArrayList;
import java.util.List;

/**
 * 音符盒弹力球运行时：过滤半径 。收集同一 tick 内激活的目标 。在服务端 tick 。 * 一次性交给分配器（保证多音符盒同帧激活时均匀分布），并向客户端广播分配结果。 */
public final class NoteBlockRuntime implements NoteBlockListener {

    private final NoteBlockParams config;
    private final BouncyBallDistributor distributor;
    private MinecraftServer server;
    private long batchTick = -1;
    private final List<Vec3d> batchTargets = new ArrayList<>();
    private final List<Integer> batchNotes = new ArrayList<>();

    public NoteBlockRuntime(NoteBlockParams config) {
        this.config = config;
        this.distributor = new BouncyBallDistributor(config.ballCount());
    }

    public void bindServer(MinecraftServer server) {
        this.server = server;
    }

    public NoteBlockParams config() {
        return config;
    }

    @Override
    public void onNoteBlock(ServerWorld world, BlockPos pos, int pitch) {
        if (server == null) {
            return;
        }
        Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        Vec3d center = config.center();
        double distH = Math.hypot(target.getX() - center.getX(), target.getZ() - center.getZ());
        if (distH > config.radius()) {
            return;
        }
        long tick = server.getTicks();
        if (tick != batchTick) {
            // 新的一 tick：先把上一 tick 收集的目标批量分配，再重新开始收集。
            flush();
            batchTick = tick;
        }
        batchTargets.add(target);
        batchNotes.add(pitch);
    }

    /**
     * 服务。tick 末调用，把本 tick 收集的目标批量分配并广播。     */
    public void tick(MinecraftServer server) {
        if (this.server != null && this.server != server) {
            return;
        }
        flush();
        batchTick = -1;
    }

    private void flush() {
        if (batchTargets.isEmpty()) {
            return;
        }
        List<BouncyBallDistributor.Assignment> assignments =
                distributor.pushTargets(batchTargets, batchNotes);
        batchTargets.clear();
        batchNotes.clear();
        broadcastAssignments(assignments);
    }

    public void onBallLanded(int ballIndex) {
        if (server == null) {
            return;
        }
        broadcastAssignments(distributor.onBallLanded(ballIndex));
    }

    private void broadcastAssignments(List<BouncyBallDistributor.Assignment> assignments) {
        for (BouncyBallDistributor.Assignment a : assignments) {
            MiauParticleEffectsPackets.broadcast(
                    server,
                    new MiauParticleEffectsPackets.NoteBlockActivationPayload(a.ballIndex(), a.target(), a.note()));
        }
    }
}
