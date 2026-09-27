package org.miau.particleeffects.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.NoteBlock;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.miau.particleeffects.noteblock.NoteBlockEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 鎷︽埅 NoteBlock.playNote锛氬綋闊崇鐩掕婵€娲诲彂澹版椂锛堟湇鍔＄渚э級鍙戝竷浜嬩欢锛? * 渚?NoteBlockEvents 鐩戝惉鑰咃紙NoteBlockRuntime锛夋寜鍗婂緞杩囨护鍚庤Е鍙戝脊鍔涚悆銆? */
@Mixin(NoteBlock.class)
public abstract class NoteBlockMixin {

    @Inject(
            method = "playNote(Lnet/minecraft/entity/Entity;Lnet/minecraft/block/BlockState;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)V",
            at = @At("HEAD")
    )
    private void MiauParticleEffects$onPlayNote(Entity entity, BlockState state, World world, BlockPos pos, CallbackInfo ci) {
        if (world instanceof ServerWorld serverWorld) {
            int note = state.get(NoteBlock.NOTE);
            NoteBlockEvents.publish(serverWorld, pos, note);
        }
    }
}