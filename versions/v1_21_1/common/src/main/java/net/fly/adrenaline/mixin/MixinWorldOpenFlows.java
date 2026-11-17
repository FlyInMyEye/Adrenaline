package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BackgroundWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldOpenFlows.class)
public class MixinWorldOpenFlows {

    @Inject(method = {"openWorld", "createFreshLevel", "createLevelFromExistingSettings"}, at = @At("HEAD"))
    private void adrenaline$awaitSaveBeforeLoad(CallbackInfo ci) {
        BackgroundWorldSave.awaitCompletion();
    }

    @Inject(method = {"recreateWorldData", "loadWorldStem", "loadWorldDataBlocking"}, at = @At("HEAD"))
    private void adrenaline$awaitSaveBeforeContextLoad(CallbackInfoReturnable<?> cir) {
        BackgroundWorldSave.awaitCompletion();
    }
}
