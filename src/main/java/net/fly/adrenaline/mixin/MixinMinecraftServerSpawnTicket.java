package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = MinecraftServer.class, priority = 900)
public class MixinMinecraftServerSpawnTicket {

    @WrapOperation(method = "prepareLevels", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private <T> void adrenaline$useConfiguredSpawnZoneRadiusForTickets(ServerChunkCache chunkSource, TicketType<T> ticketType, ChunkPos chunkPos, int radius, T identifier, Operation<Void> original) {
        int configuredRadius = AdrenalineConfig.internalSpawnPreparationRadius();
        original.call(chunkSource, ticketType, chunkPos, configuredRadius == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? 0 : configuredRadius, identifier);
    }
}
