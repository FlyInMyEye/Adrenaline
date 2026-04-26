package net.fly.adrenaline.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class MixinWorldGenRegion {

    @Shadow
    public abstract ChunkAccess getChunk(int chunkX, int chunkZ);

    @Inject(method = "getBlockEntity", at = @At("RETURN"), cancellable = true)
    private void adrenaline$ensureWorldgenBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (cir.getReturnValue() != null) {
            return;
        }

        ChunkAccess chunk = this.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        BlockState state = chunk.getBlockState(pos);
        if (!state.hasBlockEntity() || !(state.getBlock() instanceof EntityBlock entityBlock)) {
            return;
        }

        BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
        if (blockEntity == null) {
            return;
        }

        chunk.setBlockEntity(blockEntity);
        cir.setReturnValue(blockEntity);
    }
}
