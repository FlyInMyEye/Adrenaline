package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Blender.class)
public class MixinBlender {

    @Unique
    private static final ThreadLocal<WorldGenRegion> ADRENALINE_LAST_REGION = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Blender> ADRENALINE_LAST_BLENDER = new ThreadLocal<>();

    @Inject(method = "of", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$reuseBlender(WorldGenRegion region, CallbackInfoReturnable<Blender> cir) {
        if (!AdrenalineConfig.get().worldgenOptimizations) {
            return;
        }

        if (region != null && region == ADRENALINE_LAST_REGION.get()) {
            Blender blender = ADRENALINE_LAST_BLENDER.get();
            if (blender != null) {
                cir.setReturnValue(blender);
            }
        }
    }

    @Inject(method = "of", at = @At("RETURN"))
    private static void adrenaline$cacheBlender(WorldGenRegion region, CallbackInfoReturnable<Blender> cir) {
        if (!AdrenalineConfig.get().worldgenOptimizations) {
            ADRENALINE_LAST_REGION.remove();
            ADRENALINE_LAST_BLENDER.remove();
            return;
        }

        ADRENALINE_LAST_REGION.set(region);
        ADRENALINE_LAST_BLENDER.set(cir.getReturnValue());
    }
}
