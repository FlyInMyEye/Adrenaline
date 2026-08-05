package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.LevelLoadingScreenExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MixinScreen {

    @Inject(method = "init(Lnet/minecraft/client/Minecraft;II)V", at = @At("TAIL"))
    private void adrenaline$initializeLevelLoadingScreen(Minecraft minecraft, int width, int height, CallbackInfo ci) {
        if ((Object) this instanceof LevelLoadingScreenExtension extension) {
            extension.adrenaline$addJoiningControls();
        }
    }
}
