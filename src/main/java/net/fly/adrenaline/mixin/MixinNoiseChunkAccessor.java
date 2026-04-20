package net.fly.adrenaline.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(NoiseChunk.class)
public interface MixinNoiseChunkAccessor {

    @Invoker("cellWidth")
    int adrenaline$cellWidth();

    @Invoker("cellHeight")
    int adrenaline$cellHeight();

    @Invoker("getInterpolatedState")
    BlockState adrenaline$getInterpolatedState();
}
