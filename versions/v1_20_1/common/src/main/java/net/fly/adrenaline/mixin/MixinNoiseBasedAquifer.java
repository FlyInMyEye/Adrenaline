package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.NativeAquiferSampler;
import net.fly.adrenaline.worldgen.AdrenalineFastAquiferAccess;
import net.fly.adrenaline.worldgen.AdrenalineFluidStatusAccess;
import net.fly.adrenaline.worldgen.AdrenalineNativeAquiferAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.commons.lang3.mutable.MutableDouble;

import javax.annotation.Nullable;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class MixinNoiseBasedAquifer implements AdrenalineFastAquiferAccess, AdrenalineNativeAquiferAccess {

    @Unique
    private static final Map<PositionalRandomFactory, AdrenalineSharedFluidStatuses> adrenaline$sharedFluidStatuses = new IdentityHashMap<>();

    @Unique
    private static final Object adrenaline$sharedFluidStatusesLock = new Object();

    @Unique
    private final MutableDouble adrenaline$barrierNoise = new MutableDouble(Double.NaN);

    @Shadow @Final private long[] aquiferLocationCache;
    @Shadow @Final private Aquifer.FluidStatus[] aquiferCache;
    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;
    @Shadow @Final private int minGridX;
    @Shadow @Final private int minGridY;
    @Shadow @Final private int minGridZ;
    @Shadow @Final private int gridSizeX;
    @Shadow @Final private int gridSizeZ;
    @Shadow @Final private PositionalRandomFactory positionalRandomFactory;
    @Shadow @Final protected DensityFunction barrierNoise;
    @Shadow protected boolean shouldScheduleFluidUpdate;

    @Shadow
    protected abstract Aquifer.FluidStatus computeFluid(int x, int y, int z);

    @Shadow
    protected abstract Aquifer.FluidStatus getAquiferStatus(long location);

    @Shadow
    protected abstract double calculatePressure(DensityFunction.FunctionContext context, MutableDouble barrierNoise,
                                                Aquifer.FluidStatus first, Aquifer.FluidStatus second);

    @Unique
    private short[] adrenaline$packedAquiferLocations;

    @Unique
    private int[] adrenaline$searchCacheIndices;

    @Unique
    private int[] adrenaline$searchDistances;

    @Unique
    private int[] adrenaline$searchDeltaZ;

    @Unique
    private int adrenaline$lastSearchX = Integer.MIN_VALUE;

    @Unique
    private int adrenaline$lastSearchY;

    @Unique
    private int adrenaline$lastSearchZ;

    @Unique
    private int adrenaline$lastSearchGridX;

    @Unique
    private int adrenaline$lastSearchGridY;

    @Unique
    private int adrenaline$lastSearchGridZ;

    @Unique
    private int[] adrenaline$nativeFluidLevels;

    @Unique
    private byte[] adrenaline$nativeFluidTypes;

    @Unique
    private boolean adrenaline$nativeFluidTableReady;

    @Unique
    private boolean adrenaline$nativeFluidTableUnsupported;

    @Unique
    private double[] adrenaline$nativeBarrierValues;

    @Unique
    private byte[] adrenaline$nativeMaterials;

    @Unique
    private int[] adrenaline$nativeDeferredIndices;

    @Unique
    private final int[] adrenaline$nativeDeferredCount = new int[1];

    @Unique
    private long[] adrenaline$nativeCandidates;

    @Unique
    private int adrenaline$nativeGlobalFluidLevel;

    @Unique
    private byte adrenaline$nativeGlobalFluidType;

    @Unique
    private boolean adrenaline$nativeGlobalFluidReady;

    @Unique
    private boolean adrenaline$nativeGlobalFluidUnsupported;

    @Unique
    private AdrenalineSharedFluidStatuses adrenaline$sharedFluidStatusCache;

    @Unique
    @Inject(method = "<init>", at = @At("TAIL"))
    @ControlsOptimization(Optimization.AQUIFER)
    private void adrenaline$prewarmCenterCache(CallbackInfo ci) {
        if (!AdrenalineConfig.aquiferOptimizationsEnabled() || aquiferLocationCache.length > 4096) {
            return;
        }

        int gridPlane = gridSizeX * gridSizeZ;
        int sizeY = aquiferLocationCache.length / gridPlane;
        adrenaline$packedAquiferLocations = new short[aquiferLocationCache.length];
        adrenaline$searchCacheIndices = new int[12];
        adrenaline$searchDistances = new int[12];
        adrenaline$searchDeltaZ = new int[12];
        adrenaline$nativeFluidLevels = new int[aquiferLocationCache.length];
        adrenaline$nativeFluidTypes = new byte[aquiferLocationCache.length];
        if (AdrenalineConfig.nativeAquiferBatchingEnabled()) {
            this.adrenaline$sharedFluidStatusCache = adrenaline$sharedFluidStatusCache(this.positionalRandomFactory);
        }
        for (int dy = 0; dy < sizeY; dy++) {
            for (int dz = 0; dz < gridSizeZ; dz++) {
                for (int dx = 0; dx < gridSizeX; dx++) {
                    int cx = minGridX + dx;
                    int cy = minGridY + dy;
                    int cz = minGridZ + dz;
                    int index = (dy * gridSizeZ + dz) * gridSizeX + dx;
                    RandomSource random = positionalRandomFactory.at(cx, cy, cz);
                    int offsetX = random.nextInt(10);
                    int offsetY = random.nextInt(9);
                    int offsetZ = random.nextInt(10);
                    aquiferLocationCache[index] = BlockPos.asLong(cx * 16 + offsetX, cy * 12 + offsetY, cz * 16 + offsetZ);
                    adrenaline$packedAquiferLocations[index] = (short) (offsetX << 8 | offsetY << 4 | offsetZ);
                }
            }
        }
    }

    @Overwrite
    @Nullable
    @ControlsOptimization(Optimization.AQUIFER)
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
        if (adrenaline$packedAquiferLocations != null) {
            return adrenaline$computeSubstanceFast(context, density);
        }
        return adrenaline$computeSubstanceVanilla(context, density);
    }

    @Override
    public boolean adrenaline$supportsPrecomputedMaterials() {
        return adrenaline$packedAquiferLocations != null;
    }

    @Override
    public boolean adrenaline$prepareNativeMaterials(DensityFunction.ContextProvider contextProvider, double[] densityValues, int baseX, int baseY, int baseZ, int cellWidth, int cellHeight) {
        if (!AdrenalineConfig.nativeAquiferBatchingEnabled() || adrenaline$packedAquiferLocations == null
            || densityValues.length != cellWidth * cellWidth * cellHeight || !adrenaline$prepareNativeFluidTable() || !adrenaline$prepareNativeGlobalFluid()) {
            return false;
        }

        int count = densityValues.length;
        if (adrenaline$nativeBarrierValues == null || adrenaline$nativeBarrierValues.length < count) {
            adrenaline$nativeBarrierValues = new double[count];
            adrenaline$nativeMaterials = new byte[count];
            adrenaline$nativeDeferredIndices = new int[count];
            adrenaline$nativeCandidates = new long[count * 2];
        }

        boolean prepared = NativeAquiferSampler.prepare(densityValues, adrenaline$nativeCandidates, adrenaline$packedAquiferLocations,
            adrenaline$nativeFluidLevels, adrenaline$nativeFluidTypes, adrenaline$nativeGlobalFluidLevel, adrenaline$nativeGlobalFluidType, minGridX, minGridY, minGridZ, gridSizeX, gridSizeZ,
            baseX, baseY, baseZ, cellWidth, cellHeight, adrenaline$nativeDeferredIndices, adrenaline$nativeDeferredCount, adrenaline$nativeMaterials);
        if (!prepared) {
            return false;
        }
        int deferredCount = adrenaline$nativeDeferredCount[0];
        if (deferredCount < 0 || deferredCount > count) {
            return false;
        }
        for (int deferredIndex = 0; deferredIndex < deferredCount; deferredIndex++) {
            int index = adrenaline$nativeDeferredIndices[deferredIndex];
            if (index < 0 || index >= count) {
                return false;
            }
            adrenaline$nativeBarrierValues[index] = this.barrierNoise.compute(contextProvider.forIndex(index));
        }
        contextProvider.forIndex(count - 1);
        return deferredCount == 0 || NativeAquiferSampler.evaluate(densityValues, adrenaline$nativeBarrierValues, adrenaline$nativeCandidates,
            adrenaline$nativeFluidLevels, adrenaline$nativeFluidTypes, adrenaline$nativeGlobalFluidLevel, adrenaline$nativeGlobalFluidType, baseY, cellWidth, cellHeight, adrenaline$nativeDeferredIndices, deferredCount, adrenaline$nativeMaterials);
    }

    @Override
    public byte adrenaline$nativeMaterialAt(int index) {
        return adrenaline$nativeMaterials[index];
    }

    @Unique
    private boolean adrenaline$prepareNativeFluidTable() {
        if (adrenaline$nativeFluidTableReady) {
            return true;
        }
        if (adrenaline$nativeFluidTableUnsupported) {
            return false;
        }
        for (int index = 0; index < aquiferCache.length; index++) {
            Aquifer.FluidStatus status = adrenaline$getAquiferStatus(index);
            AdrenalineFluidStatusAccess access = (AdrenalineFluidStatusAccess) (Object) status;
            int material = adrenaline$materialCode(access.adrenaline$fluidType());
            if (material != 2 && material != 3) {
                adrenaline$nativeFluidTableUnsupported = true;
                return false;
            }
            adrenaline$nativeFluidLevels[index] = access.adrenaline$fluidLevel();
            adrenaline$nativeFluidTypes[index] = (byte) material;
        }
        adrenaline$nativeFluidTableReady = true;
        return true;
    }

    @Unique
    private boolean adrenaline$prepareNativeGlobalFluid() {
        if (adrenaline$nativeGlobalFluidReady) {
            return true;
        }
        if (adrenaline$nativeGlobalFluidUnsupported) {
            return false;
        }
        Aquifer.FluidStatus status = this.globalFluidPicker.computeFluid(0, 320, 0);
        AdrenalineFluidStatusAccess access = (AdrenalineFluidStatusAccess) (Object) status;
        int material = adrenaline$materialCode(access.adrenaline$fluidType());
        if (material != 2 && material != 3) {
            adrenaline$nativeGlobalFluidUnsupported = true;
            return false;
        }
        adrenaline$nativeGlobalFluidLevel = access.adrenaline$fluidLevel();
        adrenaline$nativeGlobalFluidType = (byte) material;
        adrenaline$nativeGlobalFluidReady = true;
        return true;
    }

    @Unique
    private static int adrenaline$materialCode(BlockState state) {
        if (state.is(Blocks.AIR)) {
            return 1;
        }
        if (state.is(Blocks.WATER)) {
            return 2;
        }
        if (state.is(Blocks.LAVA)) {
            return 3;
        }
        return -1;
    }

    @Unique
    @Nullable
    private BlockState adrenaline$computeSubstanceFast(DensityFunction.FunctionContext context, double density) {
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        if (density > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus globalStatus = globalFluidPicker.computeFluid(x, y, z);
        if (globalStatus.at(y).is(Blocks.LAVA)) {
            shouldScheduleFluidUpdate = false;
            return Blocks.LAVA.defaultBlockState();
        }

        int gridX = x - 5 >> 4;
        int gridY = Math.floorDiv(y + 1, 12);
        int gridZ = z - 5 >> 4;
        int localGridX = gridX - minGridX;
        int localGridY = gridY - minGridY;
        int localGridZ = gridZ - minGridZ;
        int nearest = Integer.MAX_VALUE;
        int secondNearest = Integer.MAX_VALUE;
        int thirdNearest = Integer.MAX_VALUE;
        boolean reusableSearch = x == adrenaline$lastSearchX && y == adrenaline$lastSearchY && z >= adrenaline$lastSearchZ
            && gridX == adrenaline$lastSearchGridX && gridY == adrenaline$lastSearchGridY && gridZ == adrenaline$lastSearchGridZ;
        if (reusableSearch) {
            int zAdvance = z - adrenaline$lastSearchZ;
            for (int order = 0; order < 12; order++) {
                int oldDeltaZ = adrenaline$searchDeltaZ[order];
                int newDeltaZ = oldDeltaZ - zAdvance;
                int distance;
                if (zAdvance == 1) {
                    distance = adrenaline$searchDistances[order] - 2 * oldDeltaZ + 1;
                } else {
                    distance = adrenaline$searchDistances[order] + newDeltaZ * newDeltaZ - oldDeltaZ * oldDeltaZ;
                }
                adrenaline$searchDeltaZ[order] = newDeltaZ;
                adrenaline$searchDistances[order] = distance;
                int packedCandidate = distance << 16 | (11 - order) << 12 | adrenaline$searchCacheIndices[order];
                if (packedCandidate <= nearest) {
                    thirdNearest = secondNearest;
                    secondNearest = nearest;
                    nearest = packedCandidate;
                } else if (packedCandidate <= secondNearest) {
                    thirdNearest = secondNearest;
                    secondNearest = packedCandidate;
                } else if (packedCandidate <= thirdNearest) {
                    thirdNearest = packedCandidate;
                }
            }
        } else {
            int order = 0;
            for (int offsetX = 0; offsetX <= 1; offsetX++) {
                int centerGridX = gridX + offsetX;
                int localX = localGridX + offsetX;
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    int centerGridY = gridY + offsetY;
                    int localY = localGridY + offsetY;
                    int row = (localY * gridSizeZ + localGridZ) * gridSizeX + localX;
                    for (int offsetZ = 0; offsetZ <= 1; offsetZ++) {
                        int cacheIndex = row + offsetZ * gridSizeX;
                        int packedLocation = adrenaline$packedAquiferLocations[cacheIndex] & 0xFFFF;
                        int deltaX = centerGridX * 16 + (packedLocation >> 8) - x;
                        int deltaY = centerGridY * 12 + (packedLocation >> 4 & 15) - y;
                        int deltaZ = (gridZ + offsetZ) * 16 + (packedLocation & 15) - z;
                        int distance = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                        adrenaline$searchCacheIndices[order] = cacheIndex;
                        adrenaline$searchDistances[order] = distance;
                        adrenaline$searchDeltaZ[order] = deltaZ;
                        int packedCandidate = distance << 16 | (11 - order) << 12 | cacheIndex;
                        if (packedCandidate <= nearest) {
                            thirdNearest = secondNearest;
                            secondNearest = nearest;
                            nearest = packedCandidate;
                        } else if (packedCandidate <= secondNearest) {
                            thirdNearest = secondNearest;
                            secondNearest = packedCandidate;
                        } else if (packedCandidate <= thirdNearest) {
                            thirdNearest = packedCandidate;
                        }
                        order++;
                    }
                }
            }
        }
        adrenaline$lastSearchX = x;
        adrenaline$lastSearchY = y;
        adrenaline$lastSearchZ = z;
        adrenaline$lastSearchGridX = gridX;
        adrenaline$lastSearchGridY = gridY;
        adrenaline$lastSearchGridZ = gridZ;
        int nearestDistance = nearest >>> 16;
        int secondDistance = secondNearest >>> 16;
        int thirdDistance = thirdNearest >>> 16;
        Aquifer.FluidStatus nearestStatus = adrenaline$getAquiferStatus(nearest & 4095);
        double nearestSimilarity = adrenaline$similarity(nearestDistance, secondDistance);
        BlockState result = nearestStatus.at(y);
        if (nearestSimilarity <= 0.0D) {
            shouldScheduleFluidUpdate = nearestSimilarity >= -0.76D;
            return result;
        }

        if (result.is(Blocks.WATER) && globalFluidPicker.computeFluid(x, y - 1, z).at(y - 1).is(Blocks.LAVA)) {
            shouldScheduleFluidUpdate = true;
            return result;
        }

        MutableDouble barrierNoise = this.adrenaline$barrierNoise;
        barrierNoise.setValue(Double.NaN);
        Aquifer.FluidStatus secondStatus = adrenaline$getAquiferStatus(secondNearest & 4095);
        double pressure = nearestSimilarity * calculatePressure(context, barrierNoise, nearestStatus, secondStatus);
        if (density + pressure > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus thirdStatus = adrenaline$getAquiferStatus(thirdNearest & 4095);
        double nearestThirdSimilarity = adrenaline$similarity(nearestDistance, thirdDistance);
        if (nearestThirdSimilarity > 0.0D
            && density + nearestSimilarity * nearestThirdSimilarity * calculatePressure(context, barrierNoise, nearestStatus, thirdStatus) > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        double secondThirdSimilarity = adrenaline$similarity(secondDistance, thirdDistance);
        if (secondThirdSimilarity > 0.0D
            && density + nearestSimilarity * secondThirdSimilarity * calculatePressure(context, barrierNoise, secondStatus, thirdStatus) > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        shouldScheduleFluidUpdate = true;
        return result;
    }

    @Unique
    @Nullable
    private BlockState adrenaline$computeSubstanceVanilla(DensityFunction.FunctionContext context, double density) {
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        if (density > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus globalStatus = globalFluidPicker.computeFluid(x, y, z);
        if (globalStatus.at(y).is(Blocks.LAVA)) {
            shouldScheduleFluidUpdate = false;
            return Blocks.LAVA.defaultBlockState();
        }

        int gridX = Math.floorDiv(x - 5, 16);
        int gridY = Math.floorDiv(y + 1, 12);
        int gridZ = Math.floorDiv(z - 5, 16);
        int nearestDistance = Integer.MAX_VALUE;
        int secondDistance = Integer.MAX_VALUE;
        int thirdDistance = Integer.MAX_VALUE;
        long nearestLocation = 0L;
        long secondLocation = 0L;
        long thirdLocation = 0L;

        for (int offsetX = 0; offsetX <= 1; offsetX++) {
            for (int offsetY = -1; offsetY <= 1; offsetY++) {
                for (int offsetZ = 0; offsetZ <= 1; offsetZ++) {
                    int centerGridX = gridX + offsetX;
                    int centerGridY = gridY + offsetY;
                    int centerGridZ = gridZ + offsetZ;
                    int cacheIndex = ((centerGridY - minGridY) * gridSizeZ + centerGridZ - minGridZ) * gridSizeX
                        + centerGridX - minGridX;
                    long location = aquiferLocationCache[cacheIndex];
                    if (location == Long.MAX_VALUE) {
                        RandomSource random = positionalRandomFactory.at(centerGridX, centerGridY, centerGridZ);
                        location = BlockPos.asLong(centerGridX * 16 + random.nextInt(10), centerGridY * 12 + random.nextInt(9),
                            centerGridZ * 16 + random.nextInt(10));
                        aquiferLocationCache[cacheIndex] = location;
                    }

                    int deltaX = BlockPos.getX(location) - x;
                    int deltaY = BlockPos.getY(location) - y;
                    int deltaZ = BlockPos.getZ(location) - z;
                    int distance = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                    if (nearestDistance >= distance) {
                        thirdLocation = secondLocation;
                        secondLocation = nearestLocation;
                        nearestLocation = location;
                        thirdDistance = secondDistance;
                        secondDistance = nearestDistance;
                        nearestDistance = distance;
                    } else if (secondDistance >= distance) {
                        thirdLocation = secondLocation;
                        secondLocation = location;
                        thirdDistance = secondDistance;
                        secondDistance = distance;
                    } else if (thirdDistance >= distance) {
                        thirdLocation = location;
                        thirdDistance = distance;
                    }
                }
            }
        }

        Aquifer.FluidStatus nearestStatus = getAquiferStatus(nearestLocation);
        double nearestSimilarity = adrenaline$similarity(nearestDistance, secondDistance);
        BlockState result = nearestStatus.at(y);
        if (nearestSimilarity <= 0.0D) {
            shouldScheduleFluidUpdate = nearestSimilarity >= -0.76D;
            return result;
        }
        if (result.is(Blocks.WATER) && globalFluidPicker.computeFluid(x, y - 1, z).at(y - 1).is(Blocks.LAVA)) {
            shouldScheduleFluidUpdate = true;
            return result;
        }

        MutableDouble barrierNoise = new MutableDouble(Double.NaN);
        Aquifer.FluidStatus secondStatus = getAquiferStatus(secondLocation);
        if (density + nearestSimilarity * calculatePressure(context, barrierNoise, nearestStatus, secondStatus) > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus thirdStatus = getAquiferStatus(thirdLocation);
        double nearestThirdSimilarity = adrenaline$similarity(nearestDistance, thirdDistance);
        if (nearestThirdSimilarity > 0.0D
            && density + nearestSimilarity * nearestThirdSimilarity * calculatePressure(context, barrierNoise, nearestStatus, thirdStatus) > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        double secondThirdSimilarity = adrenaline$similarity(secondDistance, thirdDistance);
        if (secondThirdSimilarity > 0.0D
            && density + nearestSimilarity * secondThirdSimilarity * calculatePressure(context, barrierNoise, secondStatus, thirdStatus) > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        shouldScheduleFluidUpdate = true;
        return result;
    }

    @Unique
    private Aquifer.FluidStatus adrenaline$getAquiferStatus(int index) {
        Aquifer.FluidStatus status = aquiferCache[index];
        if (status != null) {
            return status;
        }

        int packedLocation = adrenaline$packedAquiferLocations[index] & 0xFFFF;
        int localX = index % gridSizeX;
        int remaining = index / gridSizeX;
        int localZ = remaining % gridSizeZ;
        int localY = remaining / gridSizeZ;
        int x = (minGridX + localX) * 16 + (packedLocation >> 8);
        int y = (minGridY + localY) * 12 + (packedLocation >> 4 & 15);
        int z = (minGridZ + localZ) * 16 + (packedLocation & 15);
        AdrenalineSharedFluidStatuses sharedCache = this.adrenaline$sharedFluidStatusCache;
        long location = aquiferLocationCache[index];
        if (sharedCache != null) {
            status = sharedCache.adrenaline$get(location);
            if (status == null) {
                status = sharedCache.adrenaline$put(location, computeFluid(x, y, z));
            }
        } else {
            status = computeFluid(x, y, z);
        }
        aquiferCache[index] = status;
        return status;
    }

    @Unique
    private static AdrenalineSharedFluidStatuses adrenaline$sharedFluidStatusCache(PositionalRandomFactory randomFactory) {
        synchronized (adrenaline$sharedFluidStatusesLock) {
            return adrenaline$sharedFluidStatuses.computeIfAbsent(randomFactory, ignored -> new AdrenalineSharedFluidStatuses());
        }
    }

    @Unique
    private static final class AdrenalineSharedFluidStatuses {

        private static final int SHARD_COUNT = 32;
        private static final int ENTRIES_PER_SHARD = 4096;
        private final AdrenalineSharedFluidStatusShard[] shards = new AdrenalineSharedFluidStatusShard[SHARD_COUNT];

        private AdrenalineSharedFluidStatuses() {
            for (int index = 0; index < SHARD_COUNT; index++) {
                this.shards[index] = new AdrenalineSharedFluidStatusShard();
            }
        }

        private Aquifer.FluidStatus adrenaline$get(long location) {
            return this.adrenaline$shard(location).adrenaline$get(location);
        }

        private Aquifer.FluidStatus adrenaline$put(long location, Aquifer.FluidStatus status) {
            return this.adrenaline$shard(location).adrenaline$put(location, status);
        }

        private AdrenalineSharedFluidStatusShard adrenaline$shard(long location) {
            int hash = (int) (location ^ location >>> 32);
            hash ^= hash >>> 16;
            return this.shards[hash & (SHARD_COUNT - 1)];
        }
    }

    @Unique
    private static final class AdrenalineSharedFluidStatusShard {

        private final Long2ObjectLinkedOpenHashMap<Aquifer.FluidStatus> statuses = new Long2ObjectLinkedOpenHashMap<>();

        private synchronized Aquifer.FluidStatus adrenaline$get(long location) {
            return this.statuses.getAndMoveToLast(location);
        }

        private synchronized Aquifer.FluidStatus adrenaline$put(long location, Aquifer.FluidStatus status) {
            Aquifer.FluidStatus existing = this.statuses.getAndMoveToLast(location);
            if (existing != null) {
                return existing;
            }
            this.statuses.putAndMoveToLast(location, status);
            if (this.statuses.size() > AdrenalineSharedFluidStatuses.ENTRIES_PER_SHARD) {
                this.statuses.removeFirst();
            }
            return status;
        }
    }

    @Unique
    private static double adrenaline$similarity(int firstDistance, int secondDistance) {
        return 1.0D - (double) Math.abs(secondDistance - firstDistance) / 25.0D;
    }
}
