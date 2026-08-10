package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fly.adrenaline.client.InitialWorldCreationAccess;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.BackgroundWorldgenWarmupState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MinecraftServer.class, priority = 900)
public class MixinMinecraftServerSpawnTicket {

    @WrapOperation(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"), require = 0)
    @ControlsOptimization(Optimization.SPAWN_ZONE)
    private <T> void adrenaline$useConfiguredSpawnZoneRadiusForTickets(ServerChunkCache chunkSource, TicketType<T> ticketType, ChunkPos chunkPos, int radius, T identifier, Operation<Void> original) {
        if (OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_ZONE)) {
            original.call(chunkSource, ticketType, chunkPos, radius, identifier);
            return;
        }
        if (BackgroundWorldgenWarmupState.isServer((MinecraftServer) (Object) this)) {
            original.call(chunkSource, ticketType, chunkPos, BackgroundWorldgenWarmupState.spawnZoneRadius() + AdrenalineConfig.resolvedFeatureSafetyRadius() - 1, identifier);
            return;
        }
        if (!((InitialWorldCreationAccess) this).adrenaline$isInitialWorldCreation() && AdrenalineConfig.fastTerrainLoadingMode() != AdrenalineConfig.FastTerrainLoadingMode.OFF) {
            original.call(chunkSource, ticketType, chunkPos, 0, identifier);
            return;
        }
        int configuredRadius = AdrenalineConfig.internalSpawnPreparationRadius();
        original.call(chunkSource, ticketType, chunkPos, configuredRadius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? 0 : configuredRadius, identifier);
    }
}
