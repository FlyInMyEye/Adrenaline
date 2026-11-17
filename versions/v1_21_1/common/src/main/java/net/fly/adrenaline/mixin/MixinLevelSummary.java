package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BackgroundWorldSave;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelSummary.class)
public class MixinLevelSummary {

    @Shadow
    @Final
    private String levelId;

    @Inject(method = "getLevelName", at = @At("HEAD"), cancellable = true)
    private void adrenaline$showSavingWorldName(CallbackInfoReturnable<String> cir) {
        if (BackgroundWorldSave.isSaving(this.levelId)) {
            cir.setReturnValue(I18n.get("gui.adrenaline.world.saving"));
        }
    }

    @Inject(method = "getInfo", at = @At("HEAD"), cancellable = true)
    private void adrenaline$showSavingWorldInfo(CallbackInfoReturnable<Component> cir) {
        if (BackgroundWorldSave.isSaving(this.levelId)) {
            cir.setReturnValue(Component.translatable("gui.adrenaline.world.saving"));
        }
    }

    @Inject(method = "isLocked", at = @At("HEAD"), cancellable = true)
    private void adrenaline$unlockFinishedSave(CallbackInfoReturnable<Boolean> cir) {
        if (BackgroundWorldSave.isTracked(this.levelId) && !BackgroundWorldSave.isRunning()) {
            cir.setReturnValue(false);
        }
    }
}
