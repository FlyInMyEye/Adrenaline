package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BackgroundWorldSave;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Minecraft.class, priority = 900)
public class MixinBackgroundWorldSave {

    @Redirect(method = "clearLevel(Lnet/minecraft/client/gui/screens/Screen;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isShutdown()Z"), require = 0)
    @ControlsOptimization(Optimization.BACKGROUND_SAVE)
    private boolean adrenaline$saveWorldInBackground(IntegratedServer server) {
        if (!AdrenalineConfig.skipSavingScreenAfterExit()) {
            return server.isShutdown();
        }
        BackgroundWorldSave.detach(server);
        return true;
    }
}
