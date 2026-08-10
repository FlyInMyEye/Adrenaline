package net.fly.adrenaline.compat.mixin.blueprint;

import com.teamabnormals.blueprint.common.levelgen.feature.BlueprintTreeFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlueprintTreeFeature.class)
public class MixinBlueprintTreeFeature {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void syncPlace(FeaturePlaceContext ctx, CallbackInfoReturnable<Boolean> cir) {
        if (!Thread.holdsLock(this)) {
            synchronized (this) {
                cir.setReturnValue(((Feature) (Object) this).place(ctx));
            }
        }
    }
}
