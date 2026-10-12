package net.fly.adrenaline.mixin;

import net.fly.adrenaline.util.WarmupWorldDiscard;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionStorage.class)
public class MixinSectionStorageWarmup {

    @Inject(method = "hasWork", at = @At("HEAD"), cancellable = true)
    private void adrenaline$discardWarmupSections(CallbackInfoReturnable<Boolean> cir) {
        if (WarmupWorldDiscard.isDiscarding()) {
            cir.setReturnValue(false);
        }
    }
}
