package net.fly.adrenaline.util;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.IdMap;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.chunk.HashMapPalette;
import net.minecraft.world.level.chunk.LinearPalette;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.SingleValuePalette;

public final class PalettePacking {
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(Scratch::new);

    private PalettePacking() {
    }

    public static <T> PalettedContainerRO.PackedData<T> pack(PalettedContainer<T> source, BitStorage storage, Palette<T> palette, IdMap<T> registry, PalettedContainer.Strategy strategy) {
        int size = strategy.size();
        Class<?> paletteClass = palette.getClass();
        if (storage.getSize() != size || size > 4096
            || (paletteClass != SingleValuePalette.class && paletteClass != LinearPalette.class && paletteClass != HashMapPalette.class)) {
            return null;
        }
        if (storage.getClass() == ZeroBitStorage.class) {
            return new PalettedContainerRO.PackedData<>(List.of(palette.valueFor(0)), Optional.empty());
        }
        if (storage.getClass() != SimpleBitStorage.class || storage.getBits() > 8) {
            return null;
        }

        Scratch scratch = SCRATCH.get();
        Arrays.fill(scratch.remap, 0, 1 << storage.getBits(), -1);
        storage.unpack(scratch.ids);
        HashMapPalette<T> packedPalette = new HashMapPalette<>(registry, storage.getBits(), source);
        for (int i = 0; i < size; i++) {
            int sourceId = scratch.ids[i];
            int packedId = scratch.remap[sourceId];
            if (packedId == -1) {
                packedId = packedPalette.idFor(palette.valueFor(sourceId));
                scratch.remap[sourceId] = packedId;
            }
            scratch.ids[i] = packedId;
        }
        int bits = Mth.ceillog2(packedPalette.getSize());
        if (bits != 0 && strategy == PalettedContainer.Strategy.SECTION_STATES) {
            bits = Math.max(4, bits);
        }
        return new PalettedContainerRO.PackedData<>(packedPalette.getEntries(), bits == 0 ? Optional.empty()
            : Optional.of(Arrays.stream(new SimpleBitStorage(bits, size, scratch.ids).getRaw())));
    }

    private static final class Scratch {
        private final int[] ids = new int[4096];
        private final int[] remap = new int[256];
    }
}
