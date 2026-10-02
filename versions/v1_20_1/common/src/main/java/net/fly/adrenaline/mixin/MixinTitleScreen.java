package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BuildWarningOverlay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {

    @Inject(method = "render", at = @At("TAIL"))
    private void adrenaline$renderBuildWarning(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        BuildWarningOverlay.renderTitle(graphics, ((TitleScreen) (Object) this).width);
    }
}
