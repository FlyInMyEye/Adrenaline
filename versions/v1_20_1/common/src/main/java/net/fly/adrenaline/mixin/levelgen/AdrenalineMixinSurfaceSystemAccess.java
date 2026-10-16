package net.fly.adrenaline.mixin.levelgen;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SurfaceSystem.class)
public interface AdrenalineMixinSurfaceSystemAccess {
    @Accessor("noiseRandom")
    net.minecraft.world.level.levelgen.PositionalRandomFactory adrenaline$getNoiseRandom();

    @Accessor("clayBands")
    BlockState[] adrenaline$getClayBands();

    @Accessor("clayBandsOffsetNoise")
    NormalNoise adrenaline$getClayBandsOffsetNoise();

    @Invoker("getBand")
    BlockState adrenaline$getBand(int x, int y, int z);
}
