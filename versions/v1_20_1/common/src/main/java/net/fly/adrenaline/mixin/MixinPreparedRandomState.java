package net.fly.adrenaline.mixin;

import net.fly.adrenaline.util.WorldgenPreparation;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ChunkMap.class, priority = 0)
public class MixinPreparedRandomState {

    @Shadow
    @Final
    private ChunkGenerator generator;

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;Lnet/minecraft/core/HolderGetter;J)Lnet/minecraft/world/level/levelgen/RandomState;"
        ),
        require = 0
    )
    private RandomState adrenaline$usePreparedRandomState(NoiseGeneratorSettings settings, HolderGetter<NormalNoise.NoiseParameters> noises, long seed) {
        RandomState prepared = WorldgenPreparation.take(this.generator, settings, seed);
        return prepared == null ? RandomState.create(settings, noises, seed) : prepared;
    }
}
