package org.miau.particleeffects.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.miau.particleeffects.noteblock.NoteBlockEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 NoteBlock.playNote：当音符盒被激活发声时（服务端侧）发布事件，
 * 由 NoteBlockEvents 监听者（NoteBlockRuntime）按半径过滤后触发弹力球。
 */
@Mixin(NoteBlock.class)
public abstract class NoteBlockMixin {

    @Inject(
            method = "playNote(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At("HEAD")
    )
    private void MiauParticleEffects$onPlayNote(Entity entity, BlockState state, Level world, BlockPos pos, CallbackInfo ci) {
        if (world instanceof ServerLevel serverWorld) {
            int note = state.getValue(NoteBlock.NOTE);
            NoteBlockEvents.publish(serverWorld, pos, note);
        }
    }
}