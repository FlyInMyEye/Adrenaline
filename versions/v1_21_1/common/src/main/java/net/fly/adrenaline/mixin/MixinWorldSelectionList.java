package net.fly.adrenaline.mixin;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.client.BackgroundWorldgenWarmup;
import net.fly.adrenaline.client.WorldgenBenchmark;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldSelectionList.class)
public class MixinWorldSelectionList {

    @Inject(method = "filterAccepts", at = @At("HEAD"), cancellable = true)
    private void adrenaline$hideInternalWorlds(String filter, LevelSummary summary, CallbackInfoReturnable<Boolean> cir) {
        String levelId = summary.getLevelId();
        if (BackgroundWorldgenWarmup.isWarmupLevel(levelId) || BuildConfig.DEBUG && WorldgenBenchmark.isBenchmarkLevel(levelId)) {
            cir.setReturnValue(false);
        }
    }
}
