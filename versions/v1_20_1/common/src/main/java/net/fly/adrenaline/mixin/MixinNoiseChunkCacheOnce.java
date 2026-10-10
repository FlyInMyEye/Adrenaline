package net.fly.adrenaline.mixin;

import java.nio.ByteBuffer;
import net.fly.adrenaline.worldgen.AdrenalineCachedDensityAccess;
import net.fly.adrenaline.worldgen.AdrenalineCellGridAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$CacheOnce")
public abstract class MixinNoiseChunkCacheOnce implements AdrenalineCachedDensityAccess {
    @Shadow private double[] lastArray;
    @Shadow private long lastArrayCounter;

    @Override
    public boolean adrenaline$writeNativeInput(ByteBuffer target, int offset, NoiseChunk owner, AdrenalineCellGridAccess grid, int count) {
        MixinNoiseChunkAccessor access = (MixinNoiseChunkAccessor) owner;
        if (this.lastArray != null && this.lastArrayCounter == access.adrenaline$arrayInterpolationCounter()) {
            if (this.lastArray.length != count) {
                return false;
            }
            for (int i = 0; i < count; i++) {
                target.putDouble(offset + i * Double.BYTES, this.lastArray[i]);
            }
            return true;
        }
        return false;
    }
}
