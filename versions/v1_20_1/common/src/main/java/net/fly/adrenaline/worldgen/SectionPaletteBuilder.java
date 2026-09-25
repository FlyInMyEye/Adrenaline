package net.fly.adrenaline.worldgen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.IdMap;
import net.minecraft.util.BitStorage;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.ZeroBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PalettedContainer;

public final class SectionPaletteBuilder {

    private static final Access ACCESS = access();

    private SectionPaletteBuilder() {
    }

    public static boolean available() {
        return ACCESS != null;
    }

    public static BlockState[] unpackColumns(PalettedContainer<BlockState> source) {
        if (ACCESS == null) {
            throw new IllegalStateException("Palette access unavailable");
        }
        try {
            Object data = ACCESS.data.get(source);
            BitStorage storage = (BitStorage) ACCESS.storage.invoke(data);
            @SuppressWarnings("unchecked")
            Palette<BlockState> palette = (Palette<BlockState>) ACCESS.palette.invoke(data);
            int[] ids = new int[4096];
            storage.unpack(ids);
            BlockState[] states = new BlockState[4096];
            for (int column = 0; column < 256; column++) {
                for (int y = 0; y < 16; y++) {
                    states[column * 16 + y] = palette.valueFor(ids[y * 256 + column]);
                }
            }
            return states;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void commit(PalettedContainer<BlockState> target, List<BlockState> states, int[] ids) {
        if (ACCESS == null || ids.length != 4096 || states.isEmpty()) {
            throw new IllegalArgumentException();
        }
        try {
            int requestedBits = Mth.ceillog2(states.size());
            Object configuration = ACCESS.configuration.invoke(PalettedContainer.Strategy.SECTION_STATES, Block.BLOCK_STATE_REGISTRY, requestedBits);
            Palette.Factory factory = (Palette.Factory) ACCESS.factory.invoke(configuration);
            int bits = (int) ACCESS.bits.invoke(configuration);
            Palette<BlockState> palette = factory.create(bits, Block.BLOCK_STATE_REGISTRY, target, states);
            int[] mapped = new int[ids.length];
            int[] paletteIds = new int[states.size()];
            for (int i = 0; i < paletteIds.length; i++) {
                paletteIds[i] = palette.idFor(states.get(i));
            }
            for (int i = 0; i < mapped.length; i++) {
                mapped[i] = paletteIds[ids[i]];
            }
            BitStorage storage = bits == 0 ? new ZeroBitStorage(4096) : new SimpleBitStorage(bits, 4096, mapped);
            ACCESS.data.set(target, ACCESS.constructor.newInstance(configuration, storage, palette));
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Access access() {
        try {
            for (Field field : PalettedContainer.class.getDeclaredFields()) {
                Class<?> type = field.getType();
                RecordComponent[] components = type.getRecordComponents();
                if (Modifier.isStatic(field.getModifiers()) || components == null || components.length != 3
                    || components[1].getType() != BitStorage.class || components[2].getType() != Palette.class) {
                    continue;
                }
                field.setAccessible(true);
                Class<?> configurationType = components[0].getType();
                Method configuration = Arrays.stream(PalettedContainer.Strategy.class.getDeclaredMethods())
                    .filter(method -> method.getReturnType() == configurationType
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{IdMap.class, int.class}))
                    .findFirst().orElseThrow();
                configuration.setAccessible(true);
                RecordComponent[] configurationComponents = configurationType.getRecordComponents();
                Method factory = configurationComponents[0].getAccessor();
                Method bits = configurationComponents[1].getAccessor();
                factory.setAccessible(true);
                bits.setAccessible(true);
                Constructor<?> constructor = type.getDeclaredConstructor(configurationType, BitStorage.class, Palette.class);
                constructor.setAccessible(true);
                Method storage = components[1].getAccessor();
                Method palette = components[2].getAccessor();
                storage.setAccessible(true);
                palette.setAccessible(true);
                return new Access(field, configuration, factory, bits, constructor, storage, palette);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
        return null;
    }

    private record Access(Field data, Method configuration, Method factory, Method bits, Constructor<?> constructor, Method storage, Method palette) {
    }
}
