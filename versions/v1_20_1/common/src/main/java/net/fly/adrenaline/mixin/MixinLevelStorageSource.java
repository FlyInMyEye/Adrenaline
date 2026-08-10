package net.fly.adrenaline.mixin;

import java.util.List;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.client.BackgroundWorldgenWarmup;
import net.fly.adrenaline.client.WorldgenBenchmark;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelStorageSource.class)
public class MixinLevelStorageSource {

    @Inject(method = "findLevelCandidates", at = @At("RETURN"), cancellable = true)
    private void adrenaline$hideWarmupWorlds(CallbackInfoReturnable<LevelStorageSource.LevelCandidates> cir) {
        LevelStorageSource.LevelCandidates candidates = cir.getReturnValue();
        List<LevelStorageSource.LevelDirectory> visible = candidates.levels().stream()
            .filter(directory -> !BackgroundWorldgenWarmup.isWarmupLevel(directory.directoryName()))
            .filter(directory -> !BuildConfig.DEBUG || !WorldgenBenchmark.isBenchmarkLevel(directory.directoryName()))
            .toList();
        if (visible.size() != candidates.levels().size()) {
            cir.setReturnValue(new LevelStorageSource.LevelCandidates(visible));
        }
    }
}
