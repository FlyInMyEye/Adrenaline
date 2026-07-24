package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.apache.commons.lang3.mutable.MutableDouble;

import javax.annotation.Nullable;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class MixinNoiseBasedAquifer {

    @Shadow @Final private static int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS;
    @Shadow @Final private long[] aquiferLocationCache;
    @Shadow @Final private Aquifer.FluidStatus[] aquiferCache;
    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;
    @Shadow @Final private int minGridX;
    @Shadow @Final private int minGridY;
    @Shadow @Final private int minGridZ;
    @Shadow @Final private int gridSizeX;
    @Shadow @Final private int gridSizeZ;
    @Shadow @Final private PositionalRandomFactory positionalRandomFactory;
    @Shadow protected boolean shouldScheduleFluidUpdate;

    @Shadow
    protected abstract Aquifer.FluidStatus computeFluid(int x, int y, int z);

    @Shadow
    protected abstract Aquifer.FluidStatus getAquiferStatus(long location);

    @Shadow
    protected abstract double calculatePressure(DensityFunction.FunctionContext context, MutableDouble barrierNoise,
                                                Aquifer.FluidStatus first, Aquifer.FluidStatus second);

    @Unique
    private static final int[][] adrenaline$surfaceOffsets = new int[][]{{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    @Unique
    private short[] adrenaline$packedAquiferLocations;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void adrenaline$prewarmCenterCache(CallbackInfo ci) {
        if (!AdrenalineConfig.aquiferOptimizationsEnabled() || aquiferLocationCache.length > 4096) {
            return;
        }

        int gridPlane = gridSizeX * gridSizeZ;
        int sizeY = aquiferLocationCache.length / gridPlane;
        adrenaline$packedAquiferLocations = new short[aquiferLocationCache.length];
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
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
        if (adrenaline$packedAquiferLocations != null) {
            return adrenaline$computeSubstanceFast(context, density);
        }
        return adrenaline$computeSubstanceVanilla(context, density);
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
                    int packedCandidate = distance << 16 | (11 - order) << 12 | cacheIndex;
                    order++;
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
            }
        }

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

        Aquifer.FluidStatus secondStatus = adrenaline$getAquiferStatus(secondNearest & 4095);
        double pressure = nearestSimilarity * adrenaline$calculatePressure(y, nearestStatus, secondStatus);
        if (density + pressure > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus thirdStatus = adrenaline$getAquiferStatus(thirdNearest & 4095);
        double nearestThirdSimilarity = adrenaline$similarity(nearestDistance, thirdDistance);
        if (nearestThirdSimilarity > 0.0D
            && density + nearestSimilarity * nearestThirdSimilarity * adrenaline$calculatePressure(y, nearestStatus, thirdStatus) > 0.0D) {
            shouldScheduleFluidUpdate = false;
            return null;
        }

        double secondThirdSimilarity = adrenaline$similarity(secondDistance, thirdDistance);
        if (secondThirdSimilarity > 0.0D
            && density + nearestSimilarity * secondThirdSimilarity * adrenaline$calculatePressure(y, secondStatus, thirdStatus) > 0.0D) {
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
        status = computeFluid(x, y, z);
        aquiferCache[index] = status;
        return status;
    }

    @Unique
    private static double adrenaline$similarity(int firstDistance, int secondDistance) {
        return 1.0D - (double) Math.abs(secondDistance - firstDistance) / 25.0D;
    }

    @Unique
    private static double adrenaline$calculatePressure(int y, Aquifer.FluidStatus first, Aquifer.FluidStatus second) {
        BlockState firstState = first.at(y);
        BlockState secondState = second.at(y);
        if (firstState.is(Blocks.LAVA) && secondState.is(Blocks.WATER)
            || firstState.is(Blocks.WATER) && secondState.is(Blocks.LAVA)) {
            return 2.0D;
        }

        int firstLevel = ((MixinAquiferFluidStatusAccessor) (Object) first).adrenaline$fluidLevel();
        int secondLevel = ((MixinAquiferFluidStatusAccessor) (Object) second).adrenaline$fluidLevel();
        int levelDifference = Math.abs(firstLevel - secondLevel);
        if (levelDifference == 0) {
            return 0.0D;
        }

        double midpoint = 0.5D * (firstLevel + secondLevel);
        double offsetFromMidpoint = y + 0.5D - midpoint;
        double distanceInsideLevels = levelDifference / 2.0D - Math.abs(offsetFromMidpoint);
        double pressure;
        if (offsetFromMidpoint > 0.0D) {
            pressure = distanceInsideLevels > 0.0D ? distanceInsideLevels / 1.5D : distanceInsideLevels / 2.5D;
        } else {
            double shifted = 3.0D + distanceInsideLevels;
            pressure = shifted > 0.0D ? shifted / 3.0D : shifted / 10.0D;
        }
        return 2.0D * pressure;
    }

    @Redirect(
        method = "computeFluid",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/world/level/levelgen/Aquifer$NoiseBasedAquifer;SURFACE_SAMPLING_OFFSETS_IN_CHUNKS:[[I"
        )
    )
    private int[][] adrenaline$useReducedSurfaceSampling() {
        if (!AdrenalineConfig.aquiferOptimizationsEnabled()) {
            return SURFACE_SAMPLING_OFFSETS_IN_CHUNKS;
        }

        return adrenaline$surfaceOffsets;
    }

    @Redirect(
        method = "computeSurfaceLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/OverworldBiomeBuilder;isDeepDarkRegion(Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)Z"
        )
    )
    private boolean adrenaline$skipDeepDarkSpecialCase(DensityFunction erosion, DensityFunction depth, DensityFunction.FunctionContext context) {
        if (!AdrenalineConfig.aquiferOptimizationsEnabled()) {
            return OverworldBiomeBuilder.isDeepDarkRegion(erosion, depth, context);
        }

        return false;
    }

    @Redirect(
        method = "calculatePressure",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/DensityFunction;compute(Lnet/minecraft/world/level/levelgen/DensityFunction$FunctionContext;)D"
        )
    )
    private double adrenaline$skipBarrierNoise(DensityFunction barrierNoise, DensityFunction.FunctionContext context) {
        if (!AdrenalineConfig.aquiferOptimizationsEnabled()) {
            return barrierNoise.compute(context);
        }

        return 0.0D;
    }
}
