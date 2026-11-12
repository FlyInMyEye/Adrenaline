package net.fly.adrenaline.worldgen;

import java.nio.ByteBuffer;
import net.minecraft.world.level.levelgen.NoiseChunk;

public interface AdrenalineCachedDensityAccess {
    boolean adrenaline$writeNativeInput(ByteBuffer target, int offset, NoiseChunk owner, AdrenalineCellGridAccess grid, int count);

    default boolean adrenaline$writeNativeColumnInput(ByteBuffer target, int offset, NoiseChunk owner, int x, int z) {
        return false;
    }
}
