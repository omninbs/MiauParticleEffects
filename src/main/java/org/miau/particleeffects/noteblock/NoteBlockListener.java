package org.miau.particleeffects.noteblock;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

@FunctionalInterface
public interface NoteBlockListener {

    void onNoteBlock(ServerWorld world, BlockPos pos, int pitch);
}