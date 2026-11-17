package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BuildWarningOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class MixinGui {

    @Inject(method = "render", at = @At("TAIL"))
    private void adrenaline$renderBuildWarning(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        BuildWarningOverlay.renderGui(graphics);
    }
}
