package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, priority = 500)
public class MixinServerOptimizationTakeoverDetector {

    @Inject(method = "loadLevel", at = @At("HEAD"), require = 0)
    @ControlsOptimization(Optimization.SPAWN_ZONE)
    private void adrenaline$detectSpawnZoneProgressControl(CallbackInfo ci) {
    }

    @Inject(method = "prepareLevels", at = @At("HEAD"), require = 0)
    @ControlsOptimization(Optimization.SPAWN_ZONE)
    private void adrenaline$detectSpawnZonePreparationControl(ChunkProgressListener progressListener, CallbackInfo ci) {
    }

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), require = 0)
    @ControlsOptimization(Optimization.INITIAL_SPAWN)
    private static void adrenaline$detectInitialSpawnControl(ServerLevel level, ServerLevelData levelData, boolean generateBonusChest, boolean debugWorld, CallbackInfo ci) {
    }
}
