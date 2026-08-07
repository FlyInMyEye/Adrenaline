package net.fly.adrenaline.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class WorldgenChunkPreview {

    public static final int SIZE = 16;

    private static final ConcurrentMap<Long, int[]> PREVIEWS = new ConcurrentHashMap<>();
    private static volatile long session;
    private static volatile boolean active;

    private WorldgenChunkPreview() {
    }

    public static void begin() {
        active = false;
        session++;
        PREVIEWS.clear();
        active = true;
    }

    public static void end() {
        active = false;
        session++;
        PREVIEWS.clear();
    }

    public static boolean isActive() {
        return active;
    }

    public static int[] get(ChunkPos chunkPos) {
        return PREVIEWS.get(chunkPos.toLong());
    }

    public static void capture(ChunkAccess chunk) {
        long captureSession = session;
        if (!active) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        int minY = chunk.getMinBuildHeight();
        int maxY = minY + chunk.getHeight() - 1;
        int[] heights = new int[SIZE * SIZE];
        MapColor[] colors = new MapColor[SIZE * SIZE];
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int index = z * SIZE + x;
                int y = Math.min(
                    maxY,
                    chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1
                );
                MapColor mapColor = MapColor.NONE;

                while (y >= minY) {
                    mutablePos.set(minBlockX + x, y, minBlockZ + z);
                    BlockState state = chunk.getBlockState(mutablePos);
                    mapColor = state.getMapColor(chunk, mutablePos);
                    if (mapColor != MapColor.NONE) {
                        break;
                    }
                    y--;
                }

                heights[index] = y;
                colors[index] = mapColor;
            }
        }

        int[] pixels = new int[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int index = z * SIZE + x;
                MapColor mapColor = colors[index];
                if (mapColor == MapColor.NONE) {
                    pixels[index] = 0xFF000000;
                    continue;
                }

                int northHeight = heights[(z > 0 ? z - 1 : z) * SIZE + x];
                int westHeight = heights[z * SIZE + (x > 0 ? x - 1 : x)];
                int slope = heights[index] * 2 - northHeight - westHeight;
                MapColor.Brightness brightness = slope > 1
                    ? MapColor.Brightness.HIGH
                    : slope < -1
                        ? MapColor.Brightness.LOW
                        : MapColor.Brightness.NORMAL;
                pixels[index] = adrenaline$abgrToArgb(mapColor.calculateRGBColor(brightness));
            }
        }

        if (active && session == captureSession) {
            PREVIEWS.put(chunkPos.toLong(), pixels);
        }
    }

    private static int adrenaline$abgrToArgb(int color) {
        return color & 0xFF00FF00
            | (color & 0x00FF0000) >>> 16
            | (color & 0x000000FF) << 16;
    }
}
