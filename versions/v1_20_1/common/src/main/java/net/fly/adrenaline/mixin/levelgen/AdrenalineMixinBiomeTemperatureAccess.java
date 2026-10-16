package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.PerlinSimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Biome.class)
public interface AdrenalineMixinBiomeTemperatureAccess {
    @Accessor("TEMPERATURE_NOISE")
    static PerlinSimplexNoise adrenaline$getTemperatureNoise() {
        throw new AssertionError();
    }

    @Accessor("FROZEN_TEMPERATURE_NOISE")
    static PerlinSimplexNoise adrenaline$getFrozenTemperatureNoise() {
        throw new AssertionError();
    }

    @Invoker("getTemperature")
    float adrenaline$getTemperature(BlockPos position);

    @Invoker("getHeightAdjustedTemperature")
    float adrenaline$getHeightAdjustedTemperature(BlockPos position);

    @Invoker("coldEnoughToSnow")
    boolean adrenaline$coldEnoughToSnow(BlockPos position);
}
