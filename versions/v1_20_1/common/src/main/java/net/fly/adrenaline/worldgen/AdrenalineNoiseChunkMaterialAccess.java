package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineNoiseChunkMaterialAccess {

    boolean adrenaline$hasDirectMaterialPath();

    double[] adrenaline$finalDensityValues();

    BlockState adrenaline$calculateOre(DensityFunction.FunctionContext context);
}
