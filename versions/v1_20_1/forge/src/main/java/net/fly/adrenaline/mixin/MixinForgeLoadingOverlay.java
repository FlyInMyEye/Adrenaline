package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BackgroundWorldgenWarmup;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.loading.ForgeLoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeLoadingOverlay.class, remap = false)
public class MixinForgeLoadingOverlay {

    @Shadow
    private long fadeOutStart;

    @SuppressWarnings("target")
    @Inject(method = {"render", "m_88315_"}, at = @At("HEAD"), remap = false)
    private void adrenaline$keepStartupOverlayDuringWarmup(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.fadeOutStart >= 0L && BackgroundWorldgenWarmup.isBlocking()) {
            this.fadeOutStart = Util.getMillis();
        }
    }
}
