package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.LevelLoadStatusManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LevelLoadStatusManager.class)
public abstract class MixinLevelLoadStatusManager {

    @Shadow @Final private ClientLevel level;

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"))
    @ControlsOptimization(Optimization.TERRAIN_LOADING)
    private boolean adrenaline$waitForChunkData(LevelRenderer renderer, BlockPos position, Operation<Boolean> original) {
        if (AdrenalineConfig.fastTerrainLoadingMode() == AdrenalineConfig.FastTerrainLoadingMode.OFF) {
            return original.call(renderer, position);
        }
        return this.level.getChunkSource().hasChunk(position.getX() >> 4, position.getZ() >> 4);
    }
}
