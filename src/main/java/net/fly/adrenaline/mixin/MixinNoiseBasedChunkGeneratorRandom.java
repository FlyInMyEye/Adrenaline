package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.NonAtomicLegacyRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseBasedChunkGenerator.class)
public class MixinNoiseBasedChunkGeneratorRandom {

    @Redirect(
        method = {"applyCarvers", "spawnOriginalMobs"},
        at = @At(
            value = "NEW",
            target = "Lnet/minecraft/world/level/levelgen/LegacyRandomSource;"
        )
    )
    private LegacyRandomSource redirectLegacyRandomSource(long seed) {
        if (!AdrenalineConfig.get().fastLegacyRandom) {
            return new LegacyRandomSource(seed);
        }
        return new NonAtomicLegacyRandomSource(seed);
    }
}
