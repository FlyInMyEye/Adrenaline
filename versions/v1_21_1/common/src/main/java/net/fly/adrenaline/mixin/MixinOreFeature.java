package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.OreTargets;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration.TargetBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OreFeature.class)
public class MixinOreFeature {

    @Shadow
    protected static boolean shouldSkipAirCheck(RandomSource random, float chance) {
        throw new AssertionError();
    }

    @Inject(method = "doPlace", at = @At("HEAD"), require = 0)
    private void adrenaline$prepareTargets(WorldGenLevel level, RandomSource random, OreConfiguration config,
                                           double fromX, double toX, double fromZ, double toZ, double fromY, double toY,
                                           int minX, int minY, int minZ, int width, int height, CallbackInfoReturnable<Boolean> cir,
                                           @Share("oreTargets") LocalRef<OreTargets> targets) {
        if (AdrenalineConfig.oreTargetOptimizationsEnabled()) targets.set(OreTargets.create(config));
    }

    @WrapOperation(method = "doPlace", at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"), require = 0)
    @ControlsOptimization(value = Optimization.ORE_TARGETS, local = true)
    private Iterator<TargetBlockState> adrenaline$matchingTargets(List<TargetBlockState> list, Operation<Iterator<TargetBlockState>> original,
                                                                 @Local BlockState state, @Share("oreTargets") LocalRef<OreTargets> targets) {
        OreTargets filter = targets.get();
        return filter == null || !filter.matching(list) ? original.call(list) : filter.iterator(state);
    }

    @WrapOperation(method = "doPlace", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/feature/OreFeature;canPlaceOre(Lnet/minecraft/world/level/block/state/BlockState;Ljava/util/function/Function;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration;Lnet/minecraft/world/level/levelgen/feature/configurations/OreConfiguration$TargetBlockState;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Z"), require = 0)
    @ControlsOptimization(value = Optimization.ORE_TARGETS, local = true)
    private boolean adrenaline$reuseTargetMatch(BlockState state, Function<BlockPos, BlockState> blocks, RandomSource random,
                                                OreConfiguration config, TargetBlockState target, BlockPos.MutableBlockPos pos,
                                                Operation<Boolean> original, @Share("oreTargets") LocalRef<OreTargets> targets) {
        OreTargets filter = targets.get();
        if (filter == null || !filter.selected(state, target)) return original.call(state, blocks, random, config, target, pos);
        return shouldSkipAirCheck(random, config.discardChanceOnAirExposure) || !Feature.isAdjacentToAir(blocks, pos);
    }
}
