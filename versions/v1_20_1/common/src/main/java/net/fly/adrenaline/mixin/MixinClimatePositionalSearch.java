package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.ClimateColumnIndex;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(value = Climate.ParameterList.class, priority = 1100)
public abstract class MixinClimatePositionalSearch {

    @Definition(id = "search", method = "Lnet/minecraft/world/level/biome/Climate$RTree;search("
        + "Lnet/minecraft/world/level/biome/Climate$TargetPoint;"
        + "Lnet/minecraft/world/level/biome/Climate$DistanceMetric;)Ljava/lang/Object;", remap = true)
    @Definition(id = "distance", method = "Lnet/minecraft/world/level/biome/Climate$RTree$Node;distance([J)J", remap = true)
    @Expression("?.search(?, ::distance)")
    @WrapOperation(method = "findValuePositional", at = @At("MIXINEXTRAS:EXPRESSION"), require = 0, remap = false)
    private Object adrenaline$searchPositional(@Coerce Object tree, Climate.TargetPoint target, @Coerce Object metric, Operation<Object> original) {
        if (AdrenalineConfig.biomeSearchOptimizationsEnabled() && tree instanceof ClimateColumnIndex.Access indexedTree) {
            ClimateColumnIndex index = indexedTree.adrenaline$columnIndex();
            if (index != null && index.supports(target)) {
                Object result = index.searchPositional(target);
                if (result != ClimateColumnIndex.FALLBACK) {
                    return result;
                }
            }
        }
        return original.call(tree, target, metric);
    }
}
