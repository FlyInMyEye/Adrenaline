package net.fly.adrenaline.worldgen;

import java.util.List;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseInterpolatorView;

public final class SectionNoiseLattice implements AutoCloseable {

    private static final ThreadLocal<Storage> STORAGE = ThreadLocal.withInitial(Storage::new);

    private final Storage storage;
    private boolean closed;
    private final AdrenalineMixinNoiseInterpolatorView[] interpolators;
    private final double[][][][] slices;
    private final double[][][] originalFirst;
    private final double[][][] originalSecond;

    public SectionNoiseLattice(List<?> interpolators, int cellCountX) {
        if (cellCountX < 0 || cellCountX == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Lattice width");
        }
        this.interpolators = interpolators.toArray(AdrenalineMixinNoiseInterpolatorView[]::new);
        this.originalFirst = new double[this.interpolators.length][][];
        this.originalSecond = new double[this.interpolators.length][][];
        for (int i = 0; i < this.interpolators.length; i++) {
            this.originalFirst[i] = this.interpolators[i].adrenaline$getSlice0();
            this.originalSecond[i] = this.interpolators[i].adrenaline$getSlice1();
        }
        Storage cached = STORAGE.get();
        this.storage = cached.leased ? new Storage() : cached;
        this.storage.leased = true;
        if (this.storage.slices == null || this.storage.slices.length != cellCountX + 1
            || this.storage.slices[0].length != this.interpolators.length) {
            this.storage.slices = new double[cellCountX + 1][this.interpolators.length][][];
        }
        this.slices = this.storage.slices;
    }

    public void capture(int x, boolean first) {
        for (int i = 0; i < this.interpolators.length; i++) {
            double[][] source = first ? this.interpolators[i].adrenaline$getSlice0() : this.interpolators[i].adrenaline$getSlice1();
            double[][] copy = this.slices[x][i];
            if (copy == null || copy.length != source.length) {
                copy = new double[source.length][];
            }
            for (int z = 0; z < source.length; z++) {
                if (copy[z] == null || copy[z].length != source[z].length) {
                    copy[z] = new double[source[z].length];
                }
                System.arraycopy(source[z], 0, copy[z], 0, source[z].length);
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
        if (this.closed) {
            return;
        }
        this.closed = true;
        for (int i = 0; i < this.interpolators.length; i++) {
            this.interpolators[i].adrenaline$setSlice0(this.originalFirst[i]);
            this.interpolators[i].adrenaline$setSlice1(this.originalSecond[i]);
        }
        this.storage.leased = false;
    }

    private static final class Storage {
        private double[][][][] slices;
        private boolean leased;
    }
}
