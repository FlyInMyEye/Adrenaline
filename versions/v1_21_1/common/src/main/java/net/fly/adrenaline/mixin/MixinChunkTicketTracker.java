package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ChunkLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.server.level.DistanceManager$ChunkTicketTracker")
public class MixinChunkTicketTracker {

    @ModifyExpressionValue(method = {"<init>", "getLevel", "setLevel"}, at = @At(value = "FIELD",
        target = "Lnet/minecraft/server/level/DistanceManager$ChunkTicketTracker;MAX_LEVEL:I"))
    private static int adrenaline$useCurrentMaxLevel(int cached) {
        return ChunkLevel.MAX_LEVEL + 1;
    }
}
