package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.WorldLoadStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreenBranding {

    @Inject(method = "render", at = @At("TAIL"))
    private void adrenaline$renderWorldLoadStatus(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        String status = WorldLoadStatus.text();
        if (status != null) {
            graphics.drawString(Minecraft.getInstance().font, status, 2, ((TitleScreen) (Object) this).height - 20, 0xFFFFFFFF, true);
        }
    }
}
