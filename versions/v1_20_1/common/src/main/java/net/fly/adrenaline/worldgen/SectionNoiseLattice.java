package net.fly.adrenaline.worldgen;

import java.util.Arrays;
import java.util.List;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinNoiseInterpolatorView;
import net.fly.adrenaline.natives.NativeDensitySampler;
import net.minecraft.world.level.levelgen.DensityFunction;

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

    AirCells airCells(NativeDensityProgram program, AdrenalineNativeAquiferAccess aquifer, int baseX, int baseZ,
                        int minCellY, int cellCountY) {
        int[] firstAirCells = new int[16];
        Arrays.fill(firstAirCells, cellCountY);
        if (program == null) {
            return new AirCells(firstAirCells, null);
        }
        AdrenalineNativeInterpolatorAccess terrain = program.firstTerrainInterpolator();
        if (terrain == null) {
            DensityFunction beforeInput = program.terrainBeforeInput();
            boolean checkInput = beforeInput != null;
            CellDensityBounds.Bound density = CellDensityBounds.compile(checkInput ? beforeInput : program.root(), this.interpolators);
            if (density == null) {
                return new AirCells(firstAirCells, null);
            }
            int[] firstCandidates = checkInput ? new int[16] : firstAirCells;
            double ceiling = checkInput ? -2.0D * CellDensityBounds.INPUT_LIMIT : CellDensityBounds.AIR_LIMIT;
            for (int x = 0; x < 4; x++) {
                for (int z = 0; z < 4; z++) {
                    int firstDry = aquifer.adrenaline$firstDryCell(baseX + x * 4, baseZ + z * 4, minCellY, cellCountY);
                    firstCandidates[x * 4 + z] = firstDry < cellCountY
                        ? CellDensityBounds.firstAirCell(density, this.slices, x, z, minCellY, firstDry, cellCountY, ceiling)
                        : cellCountY;
                }
            }
            return new AirCells(firstAirCells, checkInput ? firstCandidates : null);
        }
        for (int i = 0; i < this.interpolators.length; i++) {
            if (this.interpolators[i] != terrain) {
                continue;
            }
            for (int x = 0; x < 4; x++) {
                double[][] left = this.slices[x][i];
                double[][] right = this.slices[x + 1][i];
                for (int z = 0; z < 4; z++) {
                    int firstDryCell = aquifer.adrenaline$firstDryCell(baseX + x * 4, baseZ + z * 4, minCellY, cellCountY);
                    firstAirCells[x * 4 + z] = NativeDensitySampler.firstAirCell(
                        program, left[z], left[z + 1], right[z], right[z + 1], firstDryCell, cellCountY);
                }
            }
            break;
        }
        return new AirCells(firstAirCells, null);
    }

    record AirCells(int[] unconditional, int[] needsInput) { }

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
