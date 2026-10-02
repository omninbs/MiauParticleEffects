package org.miau.particleeffects.server;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.noteblock.NoteTrack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 轨道选择与识别（服务端）。
 *
 * 流程：{@code /mpe noteblock select start} 进入选择模式 → 玩家左键点击每条轨道上的
 * 任意方块（拦截破坏、不真正破坏）记录种子 → {@code /mpe noteblock select done}
 * 时从每个种子做「同一 Y 层 + 水平相邻」的连通扩散，得到该轨道的全部音符盒。
 *
 * 之所以按“同 Y 层 + 相邻”连通：链式结构中相邻音符盒的水平间距通常是 2~3 格，
 * 而不同轨道在竖直方向上错开叠放，因此限制同一 Y 层即可避免串轨。
 */
public final class NoteTrackManager {

    /** 允许选择的轨道数上限，同时也是弹力球数量上限。 */
    public static final int MAX_TRACKS = 32;
    /** 轨道内相邻音符盒的最大水平间距（默认 3，覆盖 2~3 格的链式结构）。 */
    public static final int DEFAULT_LINK = 3;
    /** 种子周围寻找音符盒的搜索半径（种子可能落在中继器/红石线等非音符盒方块上）。 */
    private static final int SEED_RADIUS = 3;

    private static final List<BlockPos> SEEDS = new ArrayList<>();
    private static List<NoteTrack> tracks = List.of();
    private static Map<Long, Integer> posToTrack = Map.of();
    private static UUID owner;
    private static ServerLevel level;
    private static int link = DEFAULT_LINK;

    private NoteTrackManager() {
    }

    public static boolean isSelecting() {
        return owner != null;
    }

    public static int seedCount() {
        return SEEDS.size();
    }

    public static int trackCount() {
        return tracks.size();
    }

    public static List<NoteTrack> tracks() {
        return tracks;
    }

    public static int link() {
        return link;
    }

    public static boolean ownsSelection(ServerPlayer player, ServerLevel world) {
        return owner != null && owner.equals(player.getUUID()) && level == world;
    }

    /** 轨道锚点（弹力球初始停靠位置）。 */
    public static List<Vec3> anchors() {
        List<Vec3> out = new ArrayList<>(tracks.size());
        for (NoteTrack t : tracks) {
            BlockPos a = t.anchor();
            out.add(new Vec3(a.getX() + 0.5, a.getY() + 1.0, a.getZ() + 0.5));
        }
        return out;
    }

    /** 某个音符盒所属的轨道索引；不属于任何已选轨道时返回 null。 */
    public static Integer trackOf(BlockPos pos) {
        return posToTrack.get(pos.asLong());
    }

    public static void beginSelection(Player player, ServerLevel world, int linkDistance) {
        SEEDS.clear();
        owner = player.getUUID();
        level = world;
        link = linkDistance;
    }

    public static void cancelSelection() {
        SEEDS.clear();
        owner = null;
        level = null;
    }

    public static void clearSeeds() {
        SEEDS.clear();
    }

    /**
     * 玩家尝试破坏方块。选择模式下记录种子并取消破坏（返回 false）。
     */
    public static boolean onBreakAttempt(ServerLevel world, ServerPlayer player, BlockPos pos) {
        if (owner == null || !owner.equals(player.getUUID()) || level != world) {
            return true;
        }
        // 取消破坏后客户端可能已本地移除该方块，回发一次方块更新把它还原。
        player.connection.send(new ClientboundBlockUpdatePacket(pos, world.getBlockState(pos)));
        if (SEEDS.contains(pos)) {
            return false;
        }
        if (SEEDS.size() >= MAX_TRACKS) {
            actionBar(player, "§6[mpe] §c最多选择 " + MAX_TRACKS + " 条轨道，请先 /mpe noteblock select done");
            return false;
        }
        SEEDS.add(pos.immutable());
        actionBar(player, "§6[mpe] §f已选择 §e" + SEEDS.size() + " §f条轨道（上限 " + MAX_TRACKS
                + "）  输入 §e/mpe noteblock select done §f完成");
        return false;
    }

    /** 在玩家动作栏显示进度提示（26.1 起 displayClientMessage 改名为 sendOverlayMessage）。 */
    private static void actionBar(Player player, String message) {
        /*? if >=26.1 {*/
        player.sendOverlayMessage(Component.literal(message));
        /*?} else {*/
        /*player.displayClientMessage(Component.literal(message), true);
        *//*?}*/
    }

    /**
     * 结束选择：把种子扩散成轨道。返回识别出的轨道数。
     */
    public static int finishSelection(ServerLevel world, int linkDistance) {
        link = linkDistance;
        List<NoteTrack> built = new ArrayList<>();
        Set<Long> claimed = new HashSet<>();
        for (BlockPos seed : SEEDS) {
            Set<Long> set = flood(seed, world);
            if (set.isEmpty()) {
                continue;
            }
            set.removeIf(claimed::contains);
            if (set.isEmpty()) {
                continue;
            }
            built.add(new NoteTrack(built.size(), set, nearestNoteBlock(seed, set)));
            claimed.addAll(set);
        }
        if (built.isEmpty()) {
            return 0;
        }
        tracks = List.copyOf(built);
        Map<Long, Integer> map = new HashMap<>();
        for (NoteTrack t : tracks) {
            for (long l : t.noteBlocks()) {
                map.put(l, t.index());
            }
        }
        posToTrack = Map.copyOf(map);
        SEEDS.clear();
        owner = null;
        level = null;
        return tracks.size();
    }

    public static int totalNoteBlocks() {
        int n = 0;
        for (NoteTrack t : tracks) {
            n += t.size();
        }
        return n;
    }

    /** 从种子出发，在同一 Y 层内按水平距离 link 连通扩散出整条轨道。 */
    private static Set<Long> flood(BlockPos seed, ServerLevel world) {
        BlockPos start = nearestNoteBlock(seed, world);
        if (start == null) {
            return Set.of();
        }
        int y = start.getY();
        int limit = link * link;
        Set<Long> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(start.asLong());
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (int dx = -link; dx <= link; dx++) {
                for (int dz = -link; dz <= link; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (dx * dx + dz * dz > limit) {
                        continue;
                    }
                    BlockPos next = new BlockPos(cur.getX() + dx, y, cur.getZ() + dz);
                    long key = next.asLong();
                    if (visited.contains(key) || !isNoteBlock(world, next)) {
                        continue;
                    }
                    visited.add(key);
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    /** 种子周围最近的音符盒（种子可能落在中继器、红石线等非音符盒方块上）。 */
    private static BlockPos nearestNoteBlock(BlockPos seed, ServerLevel world) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -SEED_RADIUS; dx <= SEED_RADIUS; dx++) {
            for (int dy = -SEED_RADIUS; dy <= SEED_RADIUS; dy++) {
                for (int dz = -SEED_RADIUS; dz <= SEED_RADIUS; dz++) {
                    BlockPos p = new BlockPos(seed.getX() + dx, seed.getY() + dy, seed.getZ() + dz);
                    if (!isNoteBlock(world, p)) {
                        continue;
                    }
                    double dist = dx * dx + dy * dy + dz * dz;
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    private static BlockPos nearestNoteBlock(BlockPos seed, Set<Long> positions) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (long value : positions) {
            BlockPos pos = BlockPos.of(value);
            double dx = pos.getX() - seed.getX();
            double dy = pos.getY() - seed.getY();
            double dz = pos.getZ() - seed.getZ();
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDist) {
                bestDist = distance;
                best = pos;
            }
        }
        return best;
    }

    private static boolean isNoteBlock(ServerLevel world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.getBlock() instanceof NoteBlock;
    }
}
