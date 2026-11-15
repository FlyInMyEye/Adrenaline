package net.fly.adrenaline.worldgen;

import java.util.Arrays;
import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.Palette;

public final class PaletteResizeCopy {

    private PaletteResizeCopy() {
    }

    public static <T> boolean copy(Palette<T> sourcePalette, BitStorage source, Palette<T> targetPalette, BitStorage target) {
        int sourceBits = source.getBits();
        int targetBits = target.getBits();
        int count = source.getSize();
        if ((source.getClass() != SimpleBitStorage.class && source.getClass() != ZeroBitStorage.class)
            || target.getClass() != SimpleBitStorage.class || source == target || count != target.getSize()
            || count <= 0 || count > 4096 || sourceBits > 8 || targetBits < Math.max(1, sourceBits) || targetBits > 32
            || (long) sourcePalette.getSize() + targetPalette.getSize() > (1L << targetBits)) {
            return false;
        }
        long[] input = source.getRaw();
        long[] output = target.getRaw();
        if (input == output) {
            return false;
        }
        int valuesPerTarget = 64 / targetBits;
        int targetOffset = 0;
        int targetValues = 0;
        long word = 0L;
        if (sourceBits == 0) {
            int mapped = targetPalette.idFor(sourcePalette.valueFor(0));
            for (int i = 0; i < valuesPerTarget; i++) {
                word |= (long) mapped << (i * targetBits);
            }
            for (int i = 0; i < output.length; i++) {
                int values = Math.min(valuesPerTarget, count - i * valuesPerTarget);
                int bits = values * targetBits;
                long mask = bits == 64 ? -1L : (1L << bits) - 1L;
                output[i] = output[i] & ~mask | word & mask;
            }
            return true;
        }
        int[] mapping = new int[1 << sourceBits];
        Arrays.fill(mapping, -1);
        int valuesPerSource = 64 / sourceBits;
        long sourceMask = (1L << sourceBits) - 1L;
        int remaining = count;
        for (long sourceWord : input) {
            int values = Math.min(valuesPerSource, remaining);
            for (int i = 0; i < values; i++) {
                int id = (int) (sourceWord & sourceMask);
                int mapped = mapping[id];
                if (mapped < 0) {
                    mapped = targetPalette.idFor(sourcePalette.valueFor(id));
                    mapping[id] = mapped;
                }
                word |= (long) mapped << (targetValues * targetBits);
                if (++targetValues == valuesPerTarget) {
                    int bits = valuesPerTarget * targetBits;
                    long mask = bits == 64 ? -1L : (1L << bits) - 1L;
                    output[targetOffset] = output[targetOffset] & ~mask | word;
                    targetOffset++;
                    targetValues = 0;
                    word = 0L;
                }
                sourceWord >>>= sourceBits;
            }
            remaining -= values;
        }
        if (targetValues != 0) {
            long mask = (1L << (targetValues * targetBits)) - 1L;
            output[targetOffset] = output[targetOffset] & ~mask | word;
        }
        return true;
    }
}
