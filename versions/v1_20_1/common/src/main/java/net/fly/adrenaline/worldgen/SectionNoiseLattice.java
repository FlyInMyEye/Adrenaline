package net.fly.adrenaline.worldgen;

import java.util.List;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseInterpolatorView;

public final class SectionNoiseLattice implements AutoCloseable {

    private final AdrenalineMixinNoiseInterpolatorView[] interpolators;
    private final double[][][][] slices;
    private final double[][][] originalFirst;
    private final double[][][] originalSecond;

    public SectionNoiseLattice(List<?> interpolators, int cellCountX) {
        this.interpolators = interpolators.toArray(AdrenalineMixinNoiseInterpolatorView[]::new);
        this.slices = new double[cellCountX + 1][this.interpolators.length][][];
        this.originalFirst = new double[this.interpolators.length][][];
        this.originalSecond = new double[this.interpolators.length][][];
        for (int i = 0; i < this.interpolators.length; i++) {
            this.originalFirst[i] = this.interpolators[i].adrenaline$getSlice0();
            this.originalSecond[i] = this.interpolators[i].adrenaline$getSlice1();
        }
    }

    public void capture(int x, boolean first) {
        for (int i = 0; i < this.interpolators.length; i++) {
            double[][] source = first ? this.interpolators[i].adrenaline$getSlice0() : this.interpolators[i].adrenaline$getSlice1();
            double[][] copy = new double[source.length][];
            for (int z = 0; z < source.length; z++) {
                copy[z] = source[z].clone();
            }
            this.slices[x][i] = copy;
        }
    }

    public void select(int cellX) {
        for (int i = 0; i < this.interpolators.length; i++) {
            this.interpolators[i].adrenaline$setSlice0(this.slices[cellX][i]);
            this.interpolators[i].adrenaline$setSlice1(this.slices[cellX + 1][i]);
        }
    }

    @Override
    public void close() {
        for (int i = 0; i < this.interpolators.length; i++) {
            this.interpolators[i].adrenaline$setSlice0(this.originalFirst[i]);
            this.interpolators[i].adrenaline$setSlice1(this.originalSecond[i]);
        }
    }
}
