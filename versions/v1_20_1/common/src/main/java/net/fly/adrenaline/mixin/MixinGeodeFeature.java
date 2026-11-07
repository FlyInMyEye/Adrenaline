package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.worldgen.GeodeCandidates;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.GeodeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.GeodeConfiguration;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GeodeFeature.class)
public class MixinGeodeFeature {

    @WrapOperation(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"), require = 0)
    @ControlsOptimization(Optimization.GEODE_FILTER)
    private Iterable<BlockPos> adrenaline$filterGeodeVolume(BlockPos min, BlockPos max, Operation<Iterable<BlockPos>> original,
                                                           @Local(ordinal = 0) List<Pair<BlockPos, Integer>> distribution,
                                                           @Local NormalNoise noise, @Local GeodeConfiguration config) {
        GeodeCandidates candidates = GeodeCandidates.create(min, max, distribution, noise, config);
        return candidates == null ? original.call(min, max) : candidates;
    }
}
