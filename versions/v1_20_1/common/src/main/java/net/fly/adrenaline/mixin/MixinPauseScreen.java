package net.fly.adrenaline.mixin;

import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.StageTiming;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class MixinPauseScreen {

    @Unique
    private boolean adrenaline$loggedWorldgenStats;

    @Inject(method = "init", at = @At("TAIL"))
    private void adrenaline$logWorldgenStats(CallbackInfo ci) {
        if (this.adrenaline$loggedWorldgenStats || !WorldgenStageStats.isEnabled()) {
            return;
        }
        this.adrenaline$loggedWorldgenStats = true;

        StringBuilder output = new StringBuilder("Worldgen stats:");
        for (StageTiming timing : WorldgenStageStats.snapshot()) {
            output.append(' ')
                .append(timing.name().trim())
                .append('=')
                .append(timing.formatDuration());
        }
        GlobalCommon.LOGGER.info("{}", output);
    }
}
