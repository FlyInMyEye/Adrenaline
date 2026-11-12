package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.block.state.BlockState;

public interface AdrenalineFluidStatusAccess {

    int adrenaline$fluidLevel();

    BlockState adrenaline$fluidType();
}
