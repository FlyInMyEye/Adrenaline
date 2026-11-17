package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, priority = 1)
public class MixinClientOptimizationTakeoverDetector {

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V", at = @At("HEAD"))
    @ControlsOptimization(Optimization.BACKGROUND_SAVE)
    private void adrenaline$detectBackgroundSaveControl(Screen screen, boolean transferring, CallbackInfo ci) {
    }
}
