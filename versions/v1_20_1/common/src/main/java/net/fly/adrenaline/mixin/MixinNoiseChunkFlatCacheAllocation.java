package net.fly.adrenaline.mixin;

import java.nio.ByteBuffer;
import net.fly.adrenaline.worldgen.AdrenalineCachedDensityAccess;
import net.fly.adrenaline.worldgen.AdrenalineCellGridAccess;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.NoiseChunk$FlatCache")
public abstract class MixinNoiseChunkFlatCacheAllocation implements AdrenalineCachedDensityAccess {
    @Shadow @Final private double[][] values;

    @Override
    public boolean adrenaline$writeNativeInput(ByteBuffer target, int offset, NoiseChunk owner, AdrenalineCellGridAccess grid, int count) {
        MixinNoiseChunkAccessor access = (MixinNoiseChunkAccessor) owner;
        int width = grid.adrenaline$getCellWidth();
        int baseX = grid.adrenaline$getCellStartBlockX();
        int baseZ = grid.adrenaline$getCellStartBlockZ();
        int firstX = access.adrenaline$firstNoiseX();
        int firstZ = access.adrenaline$firstNoiseZ();
        int minX = QuartPos.fromBlock(baseX) - firstX;
        int minZ = QuartPos.fromBlock(baseZ) - firstZ;
        int maxX = QuartPos.fromBlock(baseX + width - 1) - firstX;
        int maxZ = QuartPos.fromBlock(baseZ + width - 1) - firstZ;
        if (minX < 0 || minZ < 0 || maxX >= this.values.length) {
            return false;
        }
        for (int x = minX; x <= maxX; x++) {
            if (maxZ >= this.values[x].length) {
                return false;
            }
        }
        for (int i = 0; i < count; i++) {
            int x = QuartPos.fromBlock(baseX + i / width % width) - firstX;
            int z = QuartPos.fromBlock(baseZ + i % width) - firstZ;
            target.putDouble(offset + i * Double.BYTES, this.values[x][z]);
        }
        return true;
    }
}
