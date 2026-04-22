package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer {

    @ModifyConstant(method = "loadLevel", constant = @Constant(intValue = 11))
    private int adrenaline$useConfiguredSpawnZoneRadiusForProgress(int radius) {
        return AdrenalineConfig.resolvedSpawnZoneRadius();
    }

    @ModifyConstant(method = "prepareLevels", constant = @Constant(intValue = 11))
    private int adrenaline$useConfiguredSpawnZoneRadiusForTickets(int radius) {
        return AdrenalineConfig.resolvedSpawnZoneRadius();
    }

    @ModifyConstant(method = "prepareLevels", constant = @Constant(intValue = 441))
    private int adrenaline$useConfiguredSpawnZoneChunkCount(int chunkCount) {
        int radius = AdrenalineConfig.resolvedSpawnZoneRadius();
        int diameter = radius * 2 - 1;
        return diameter * diameter;
    }
}
