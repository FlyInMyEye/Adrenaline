package net.fly.adrenaline.mixin;

import java.util.ArrayList;
import java.util.List;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.client.WorldgenStatsOverlay;
import net.fly.adrenaline.client.WorldgenWorkerStats;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugScreenOverlay.class)
public class MixinDebugScreenOverlay {

    @Inject(method = "getGameInformation", at = @At("RETURN"), cancellable = true)
    private void adrenaline$appendWorldgenStats(CallbackInfoReturnable<List<String>> cir) {
        if (BuildConfig.DEBUG && WorldgenStageStats.isHudVisible()) {
            List<String> lines = new ArrayList<>(cir.getReturnValue());
            lines.addAll(WorldgenStatsOverlay.lines());
            cir.setReturnValue(lines);
        }
    }

    @Inject(method = "getSystemInformation", at = @At("RETURN"), cancellable = true)
    private void adrenaline$appendWorkerStats(CallbackInfoReturnable<List<String>> cir) {
        if (BuildConfig.DEBUG && WorldgenStageStats.isHudVisible() && Minecraft.getInstance().getSingleplayerServer() != null) {
            List<String> lines = new ArrayList<>(cir.getReturnValue());
            lines.add(WorldgenWorkerStats.line());
            cir.setReturnValue(lines);
        }
    }
}
