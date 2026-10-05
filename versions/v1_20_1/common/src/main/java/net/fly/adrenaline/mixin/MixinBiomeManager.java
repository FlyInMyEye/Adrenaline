package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.BiomeFiddleCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeManager.class)
public abstract class MixinBiomeManager {
    @Shadow @Final private BiomeManager.NoiseBiomeSource noiseBiomeSource;
    @Shadow @Final private long biomeZoomSeed;

    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.BIOME_FIDDLE)
    private void adrenaline$reuseCornerOffsets(BlockPos position, CallbackInfoReturnable<Holder<Biome>> cir) {
        if (!AdrenalineConfig.biomeFiddleOptimizationsEnabled()) {
            return;
        }
        int corner = BiomeFiddleCache.selectCornerIfActive(this.biomeZoomSeed, position.getX(), position.getY(), position.getZ());
        if (corner < 0) {
            return;
        }
        int x = ((position.getX() - 2) >> 2) + ((corner & 4) == 0 ? 0 : 1);
        int y = ((position.getY() - 2) >> 2) + ((corner & 2) == 0 ? 0 : 1);
        int z = ((position.getZ() - 2) >> 2) + ((corner & 1) == 0 ? 0 : 1);
        cir.setReturnValue(this.noiseBiomeSource.getNoiseBiome(x, y, z));
    }
}
