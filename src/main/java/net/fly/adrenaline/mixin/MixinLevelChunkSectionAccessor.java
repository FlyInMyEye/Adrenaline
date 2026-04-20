package net.fly.adrenaline.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelChunkSection.class)
public interface MixinLevelChunkSectionAccessor {

    @Accessor("states")
    PalettedContainer<BlockState> adrenaline$getStates();
}
