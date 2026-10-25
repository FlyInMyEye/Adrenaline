package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.NonAtomicLegacyRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGeneratorRandom {

    @WrapOperation(
        method = {"applyCarvers", "spawnOriginalMobs"},
        at = @At(
            value = "NEW",
            target = "Lnet/minecraft/world/level/levelgen/LegacyRandomSource;"
        ),
        require = 0
    )
    @ControlsOptimization(Optimization.FAST_LEGACY_RANDOM)
    private LegacyRandomSource adrenaline$createLegacyRandomSource(long seed, Operation<LegacyRandomSource> original) {
        if (!AdrenalineConfig.fastLegacyRandomEnabled()) {
            return original.call(seed);
        }
        return new NonAtomicLegacyRandomSource(seed);
    }
}
