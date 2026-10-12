package net.fly.adrenaline.mixin;

import net.fly.adrenaline.util.WarmupWorldDiscard;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentEntitySectionManager.class)
public class MixinPersistentEntitySectionManagerWarmup {

    @Inject(method = {"autoSave", "saveAll"}, at = @At("HEAD"), cancellable = true)
    private void adrenaline$discardWarmupEntities(CallbackInfo ci) {
        if (WarmupWorldDiscard.isDiscarding()) {
            ci.cancel();
        }
    }
}
