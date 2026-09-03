package net.fly.adrenaline.worldgen;

import net.fly.adrenaline.natives.NativeDensitySampler;
import net.minecraft.world.level.levelgen.DensityFunction;

public final class NativeCellDensityEvaluator implements CellDensityEvaluator {

    private final NativeDensityProgram program;
    private final CellDensityEvaluator fallback;

    public NativeCellDensityEvaluator(NativeDensityProgram program, CellDensityEvaluator fallback) {
        this.program = program;
        this.fallback = fallback;
    }

    @Override
    public void fill(double[] values, DensityFunction.ContextProvider contextProvider) {
        boolean nativeResult = false;
        if (contextProvider instanceof AdrenalineCellGridAccess grid
            && values.length == grid.adrenaline$getCellWidth() * grid.adrenaline$getCellWidth() * grid.adrenaline$getCellHeight()) {
            this.program.prepare();
            nativeResult = NativeDensitySampler.evaluate(this.program, grid.adrenaline$getCellStartBlockX(), grid.adrenaline$getCellStartBlockY(), grid.adrenaline$getCellStartBlockZ(), grid.adrenaline$getCellWidth(), grid.adrenaline$getCellHeight(), values);
        }
        if (!nativeResult) {
            this.fallback.fill(values, contextProvider);
            return;
        }
        contextProvider.forIndex(values.length - 1);
    }
}
