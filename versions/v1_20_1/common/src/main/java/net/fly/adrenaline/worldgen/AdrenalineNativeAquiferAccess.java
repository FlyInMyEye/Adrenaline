package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;

public interface AdrenalineNativeAquiferAccess {

    boolean adrenaline$prepareNativeMaterials(DensityFunction.ContextProvider contextProvider, double[] densityValues, int baseX, int baseY, int baseZ, int cellWidth, int cellHeight);

    int adrenaline$firstDryCell(int x, int z, int minCellY, int cellCountY);

    byte adrenaline$nativeMaterialAt(int index);
}
