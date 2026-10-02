package org.miau.particleeffects.noteblock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;

@FunctionalInterface
public interface NoteBlockListener {

    void onNoteBlock(ServerLevel world, BlockPos pos, int pitch);
}