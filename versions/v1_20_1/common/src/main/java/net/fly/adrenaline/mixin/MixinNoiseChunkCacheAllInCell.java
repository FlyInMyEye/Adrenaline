package net.fly.adrenaline.mixin;

import java.nio.ByteBuffer;
import net.fly.adrenaline.worldgen.AdrenalineCachedDensityAccess;
import net.fly.adrenaline.worldgen.AdrenalineCellGridAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheAllInCell")
public abstract class MixinNoiseChunkCacheAllInCell implements AdrenalineCachedDensityAccess {
    @Shadow @Final private double[] values;

    @Override
    public boolean adrenaline$writeNativeInput(ByteBuffer target, int offset, NoiseChunk owner, AdrenalineCellGridAccess grid, int count) {
        if (!((MixinNoiseChunkAccessor) owner).adrenaline$isInterpolating() || this.values.length != count) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            target.putDouble(offset + i * Double.BYTES, this.values[i]);
        }
        return true;
    }
}
