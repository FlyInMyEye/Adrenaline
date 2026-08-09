package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineNoiseChunkMaterialAccess {

    boolean adrenaline$hasDirectMaterialPath();

    double[] adrenaline$finalDensityValues();

    boolean adrenaline$hasPrecomputedMaterialPath();

    void adrenaline$fillMaterialArrays(DensityFunction.ContextProvider contextProvider);

    BlockState adrenaline$calculateOre(DensityFunction.FunctionContext context, int index);
}
