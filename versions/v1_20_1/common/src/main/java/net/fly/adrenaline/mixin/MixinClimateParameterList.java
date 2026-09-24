package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.ClimateColumnIndex;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Climate.ParameterList.class)
public class MixinClimateParameterList<T> {

    @Unique private volatile ClimateColumnIndex adrenaline$columnIndex;
    @Unique private volatile boolean adrenaline$columnIndexInitialized;

    @WrapMethod(method = "findValueIndex(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Ljava/lang/Object;")
    @SuppressWarnings("unchecked")
    private T adrenaline$searchColumn(Climate.TargetPoint target, Operation<T> original) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return original.call(target);
        }
        if (!this.adrenaline$columnIndexInitialized) {
            this.adrenaline$initializeColumnIndex();
        }
        ClimateColumnIndex index = this.adrenaline$columnIndex;
        return index != null && index.supports(target) ? (T) index.search(target) : original.call(target);
    }

    @Unique
    private synchronized void adrenaline$initializeColumnIndex() {
        if (!this.adrenaline$columnIndexInitialized) {
            this.adrenaline$columnIndex = ClimateColumnIndex.create(this);
            this.adrenaline$columnIndexInitialized = true;
        }
    }
}
