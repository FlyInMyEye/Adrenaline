package net.fly.adrenaline.mixin;

import java.util.Locale;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.StageTiming;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen {

    @Inject(method = "init", at = @At("TAIL"))
    private void adrenaline$logWorldgenStats(CallbackInfo ci) {
        if (!WorldgenStageStats.isEnabled()) {
            return;
        }

        StringBuilder output = new StringBuilder("Worldgen stats:");
        for (StageTiming timing : WorldgenStageStats.snapshot()) {
            output.append(' ')
                .append(timing.name().trim())
                .append('=')
                .append(String.format(Locale.ROOT, "%.3fms", timing.averageNanos() / 1_000_000.0D));
        }
        GlobalCommon.LOGGER.info("{}", output);
    }
}
