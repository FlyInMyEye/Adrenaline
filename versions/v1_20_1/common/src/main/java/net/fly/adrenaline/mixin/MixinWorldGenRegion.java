package net.fly.adrenaline.mixin;

import java.util.concurrent.ConcurrentHashMap;
import net.fly.adrenaline.worldgen.AdrenalineBlenderCacheAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class MixinWorldGenRegion implements AdrenalineBlenderCacheAccess {

    @Unique
    private final ConcurrentHashMap<Thread, Blender> adrenaline$cachedBlenders = new ConcurrentHashMap<>();

    @Override
    public Blender adrenaline$getCachedBlender() {
        return this.adrenaline$cachedBlenders.get(Thread.currentThread());
    }

    @Override
    public void adrenaline$cacheBlender(Blender blender) {
        if (blender == null) {
            this.adrenaline$cachedBlenders.remove(Thread.currentThread());
        } else {
            this.adrenaline$cachedBlenders.put(Thread.currentThread(), blender);
        }
    }

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
