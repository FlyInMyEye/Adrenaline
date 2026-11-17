package net.fly.adrenaline.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

public final class WorldgenChunkPreview {

    public static final int SIZE = 16;

    private static volatile ConcurrentMap<Long, Preview> previews;

    private WorldgenChunkPreview() {
    }

    public static void begin() {
        previews = new ConcurrentHashMap<>();
    }

    public static void end() {
        previews = null;
    }

    public static boolean isActive() {
        return previews != null;
    }

    public static int[] get(ChunkPos chunkPos) {
        ConcurrentMap<Long, Preview> current = previews;
        Preview preview = current == null ? null : current.get(chunkPos.toLong());
        return preview == null ? null : preview.pixels;
    }

    public static void capture(ServerLevel level, ChunkAccess chunk) {
        ConcurrentMap<Long, Preview> current = previews;
        if (current == null || level.dimension() != Level.OVERWORLD) {
            return;
        }

        ChunkPos chunkPos = chunk.getPos();
        int minBlockX = chunkPos.getMinBlockX();
        int minBlockZ = chunkPos.getMinBlockZ();
        int minY = level.getMinBuildHeight();
        int[] heights = new int[SIZE * SIZE];
        int[] waterDepths = new int[SIZE * SIZE];
        MapColor[] colors = new MapColor[SIZE * SIZE];
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos fluidPosition = new BlockPos.MutableBlockPos();
        boolean ceiling = level.dimensionType().hasCeiling();

        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int index = z * SIZE + x;
                int blockX = minBlockX + x;
                int blockZ = minBlockZ + z;
                if (ceiling) {
                    int noise = blockX + blockZ * 231871;
                    noise = noise * noise * 31287121 + noise * 11;
                    BlockState state = (noise >> 20 & 1) == 0 ? Blocks.DIRT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    colors[index] = state.getMapColor(level, BlockPos.ZERO);
                    heights[index] = 100;
                    continue;
                }

                position.set(blockX, 0, blockZ);
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX, blockZ) + 1;
                BlockState state;
                if (y <= minY + 1) {
                    state = Blocks.BEDROCK.defaultBlockState();
                } else {
                    do {
                        position.setY(--y);
                        state = chunk.getBlockState(position);
                    } while (state.getMapColor(level, position) == MapColor.NONE && y > minY);

                    if (y > minY && !state.getFluidState().isEmpty()) {
                        int fluidY = y - 1;
                        fluidPosition.set(position);
                        BlockState below;
                        do {
                            fluidPosition.setY(fluidY--);
                            below = chunk.getBlockState(fluidPosition);
                            waterDepths[index]++;
                        } while (fluidY > minY && !below.getFluidState().isEmpty());

                        if (!state.isFaceSturdy(level, position, Direction.UP)) {
                            state = state.getFluidState().createLegacyBlock();
                        }
                    }
                }
                heights[index] = y;
                colors[index] = state.getMapColor(level, position);
            }
        }

        Preview sampled = new Preview(heights, waterDepths, colors, new int[SIZE * SIZE]);
        current.compute(chunkPos.toLong(), (key, previous) -> {
            Preview north = current.get(ChunkPos.asLong(chunkPos.x, chunkPos.z - 1));
            return sampled.shade(chunkPos, north, false);
        });
        current.computeIfPresent(ChunkPos.asLong(chunkPos.x, chunkPos.z + 1), (key, south) ->
            south.shade(new ChunkPos(chunkPos.x, chunkPos.z + 1), sampled, true)
        );
    }

    private record Preview(int[] heights, int[] waterDepths, MapColor[] colors, int[] pixels) {

        private Preview shade(ChunkPos position, Preview north, boolean borderOnly) {
            int[] shaded = borderOnly ? this.pixels.clone() : this.pixels;
            for (int z = 0; z < (borderOnly ? 1 : SIZE); z++) {
                for (int x = 0; x < SIZE; x++) {
                    int index = z * SIZE + x;
                    int parity = position.getMinBlockX() + x + position.getMinBlockZ() + z & 1;
                    MapColor color = this.colors[index];
                    MapColor.Brightness brightness;
                    if (color == MapColor.WATER) {
                        double shade = this.waterDepths[index] * 0.1D + parity * 0.2D;
                        brightness = shade < 0.5D ? MapColor.Brightness.HIGH
                            : shade > 0.9D ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    } else {
                        int previousHeight = z > 0 ? this.heights[index - SIZE]
                            : north == null ? this.heights[index] : north.heights[(SIZE - 1) * SIZE + x];
                        double shade = (this.heights[index] - previousHeight) * 4.0D / 5.0D + (parity - 0.5D) * 0.4D;
                        brightness = shade > 0.6D ? MapColor.Brightness.HIGH
                            : shade < -0.6D ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
                    }
                    shaded[index] = adrenaline$abgrToArgb(color.calculateRGBColor(brightness));
                }
            }
            return new Preview(this.heights, this.waterDepths, this.colors, shaded);
        }
    }

    private static int adrenaline$abgrToArgb(int color) {
        return color & 0xFF00FF00
            | (color & 0x00FF0000) >>> 16
            | (color & 0x000000FF) << 16;
    }
}
