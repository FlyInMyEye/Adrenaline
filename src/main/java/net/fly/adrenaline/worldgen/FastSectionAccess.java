package net.fly.adrenaline.worldgen;

import net.fly.adrenaline.mixin.MixinLevelChunkSectionAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class FastSectionAccess {

    private FastSectionAccess() {
    }

    public static BlockState writeUnchecked(LevelChunkSection section, int x, int y, int z, BlockState state) {
        PalettedContainer<BlockState> states = ((MixinLevelChunkSectionAccessor) section).adrenaline$getStates();
        return states.getAndSetUnchecked(x, y, z, state);
    }
}
