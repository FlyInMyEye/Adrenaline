package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.AdrenalineBlenderCacheAccess;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Blender.class)
public class MixinBlender {

    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.BLENDING)
    private static void adrenaline$reuseBlender(WorldGenRegion region, CallbackInfoReturnable<Blender> cir) {
        if (!AdrenalineConfig.blendingOptimizationsEnabled()) {
            return;
        }

        if (region != null) {
            Blender blender = ((AdrenalineBlenderCacheAccess) region).adrenaline$getCachedBlender();
            if (blender != null) {
                cir.setReturnValue(blender);
            }
        }
    }

    @Inject(method = "of", at = @At("RETURN"))
    private static void adrenaline$cacheBlender(WorldGenRegion region, CallbackInfoReturnable<Blender> cir) {
        if (region == null) {
            return;
        }
        AdrenalineBlenderCacheAccess cache = (AdrenalineBlenderCacheAccess) region;
        if (!AdrenalineConfig.blendingOptimizationsEnabled()) {
            cache.adrenaline$cacheBlender(null);
            return;
        }

        cache.adrenaline$cacheBlender(cir.getReturnValue());
    }
}
