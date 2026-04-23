package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class MixinNoiseBasedAquifer {

    @Shadow @Final private static int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS;
    @Shadow @Final private long[] aquiferLocationCache;
    @Shadow @Final private int minGridX;
    @Shadow @Final private int minGridY;
    @Shadow @Final private int minGridZ;
    @Shadow @Final private int gridSizeX;
    @Shadow @Final private int gridSizeZ;
    @Shadow @Final private PositionalRandomFactory positionalRandomFactory;

    @Unique
    private static final int[][] adrenaline$surfaceOffsets = new int[][]{{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    @Inject(method = "<init>", at = @At("TAIL"))
    private void adrenaline$prewarmCenterCache(CallbackInfo ci) {
        int gridPlane = gridSizeX * gridSizeZ;
        int sizeY = aquiferLocationCache.length / gridPlane;
        for (int dy = 0; dy < sizeY; dy++) {
            for (int dz = 0; dz < gridSizeZ; dz++) {
                for (int dx = 0; dx < gridSizeX; dx++) {
                    int cx = minGridX + dx;
                    int cy = minGridY + dy;
                    int cz = minGridZ + dz;
                    int index = (dy * gridSizeZ + dz) * gridSizeX + dx;
                    RandomSource random = positionalRandomFactory.at(cx, cy, cz);
                    aquiferLocationCache[index] = BlockPos.asLong(
                        cx * 16 + random.nextInt(10),
                        cy * 12 + random.nextInt(9),
                        cz * 16 + random.nextInt(10)
                    );
                }
            }
        }
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
