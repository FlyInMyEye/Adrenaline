package net.fly.adrenaline.worldgen;

public final class AquiferAirBounds {

    private AquiferAirBounds() {
    }

    public static boolean above(int levelCount, int maximumLevel, int globalLevel, int minGridX, int minGridY, int minGridZ,
                                int gridSizeX, int gridSizeZ, int baseX, int baseY, int baseZ, int cellWidth, int cellHeight) {
        if (cellWidth <= 0 || cellWidth > 32 || cellHeight <= 0 || cellHeight > 1024
            || (long) cellWidth * cellWidth * cellHeight > 1024L
            || levelCount == 0 || levelCount > 1024
            || gridSizeX <= 1 || gridSizeZ <= 1
            || gridSizeX > levelCount || gridSizeZ > levelCount
            || (long) baseY < (long) globalLevel + 5L
            || (long) baseY < (long) maximumLevel + 5L) {
            return false;
        }
        long minX = Math.floorDiv((long) baseX - 5L, 16L) - minGridX;
        long maxX = Math.floorDiv((long) baseX + cellWidth - 6L, 16L) - minGridX;
        long minZ = Math.floorDiv((long) baseZ - 5L, 16L) - minGridZ;
        long maxZ = Math.floorDiv((long) baseZ + cellWidth - 6L, 16L) - minGridZ;
        long minY = Math.floorDiv((long) baseY + 1L, 12L) - minGridY;
        long maxY = Math.floorDiv((long) baseY + cellHeight, 12L) - minGridY;
        long lastIndex = ((maxY + 1L) * gridSizeZ + maxZ + 1L) * gridSizeX + maxX + 1L;
        return minX >= 0L && maxX + 1L < gridSizeX && minZ >= 0L && maxZ + 1L < gridSizeZ
            && minY >= 1L && lastIndex < levelCount;
    }

    public static int firstDryCell(int levelCount, int maximumLevel, int globalLevel, int minGridX, int minGridY, int minGridZ,
                                   int gridSizeX, int gridSizeZ, int x, int z, int minCellY, int cellCountY) {
        long baseY = (long) minCellY * 8;
        long topCellY = baseY + ((long) cellCountY - 1) * 8;
        long dryY = (long) Math.max(globalLevel, maximumLevel) + 5;
        long firstDryCell = Math.max(0L, Math.floorDiv(dryY - baseY + 7, 8));
        if (firstDryCell >= cellCountY || baseY < Integer.MIN_VALUE || topCellY + 7 > Integer.MAX_VALUE) {
            return cellCountY;
        }
        int firstDryCellY = (int) (baseY + firstDryCell * 8);
        if (!above(levelCount, maximumLevel, globalLevel, minGridX, minGridY, minGridZ,
                gridSizeX, gridSizeZ, x, firstDryCellY, z, 4, 8)
            || !above(levelCount, maximumLevel, globalLevel, minGridX, minGridY, minGridZ,
                gridSizeX, gridSizeZ, x, (int) topCellY, z, 4, 8)) {
            return cellCountY;
        }
        return (int) firstDryCell;
    }
}
