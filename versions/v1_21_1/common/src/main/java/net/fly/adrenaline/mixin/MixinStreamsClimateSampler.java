package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.StreamsClimateCache;
import net.fly.adrenaline.worldgen.SurfaceRuleHooks;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Climate.Sampler.class)
public abstract class MixinStreamsClimateSampler {

    @Unique private static final boolean adrenaline$unmodified = adrenaline$hasUnmodifiedSampler();

    @Unique
    private static boolean adrenaline$hasUnmodifiedSampler() {
        String descriptor = "(III)" + Climate.TargetPoint.class.descriptorString();
        for (var method : Climate.Sampler.class.getDeclaredMethods()) {
            if (method.getReturnType() == Climate.TargetPoint.class && method.getParameterCount() == 3) {
                return !SurfaceRuleHooks.hooked(Climate.Sampler.class, method.getName() + descriptor);
            }
        }
        return false;
    }

    @Inject(method = "sample", at = @At("HEAD"), cancellable = true)
    private void adrenaline$reuseClimateColumn(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        if (adrenaline$unmodified) {
            Climate.TargetPoint point = StreamsClimateCache.sampleActive((Climate.Sampler) (Object) this, x, y, z);
            if (point != null) {
                cir.setReturnValue(point);
            }
        }
    }
}
