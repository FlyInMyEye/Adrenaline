package net.fly.adrenaline.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(OreVeinifier.class)
public interface MixinOreVeinifierInvoker {

    @Invoker("create")
    static NoiseChunk.BlockStateFiller adrenaline$create(DensityFunction veinToggle, DensityFunction veinRidged, DensityFunction veinGap, PositionalRandomFactory randomFactory) {
        throw new AssertionError();
    }
}
