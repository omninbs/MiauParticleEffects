package org.miau.particleeffects.noteblock;

import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * 一条链式轨道：位于同一 Y 层、水平方向彼此相邻连通的一组音符盒。
 *
 * 弹力球与轨道一一对应（第 N 条轨道由第 N 颗弹力球跟随），因此球永远不会串轨，
 * 也不会因为一次误判而被“卡”在别的轨道上。
 */
public final class NoteTrack {

    private final int index;
    private final Set<Long> noteBlocks;
    private final BlockPos anchor;

    public NoteTrack(int index, Set<Long> noteBlocks, BlockPos anchor) {
        this.index = index;
        this.noteBlocks = Set.copyOf(noteBlocks);
        this.anchor = anchor;
    }

    /** 轨道索引，同时作为该轨道弹力球的编号。 */
    public int index() {
        return index;
    }

    public Set<Long> noteBlocks() {
        return noteBlocks;
    }

    public int size() {
        return noteBlocks.size();
    }

    /** 弹力球的初始停靠点（用户点击处最近的音符盒）。 */
    public BlockPos anchor() {
        return anchor;
    }

    public boolean contains(BlockPos pos) {
        return noteBlocks.contains(pos.asLong());
    }
}
