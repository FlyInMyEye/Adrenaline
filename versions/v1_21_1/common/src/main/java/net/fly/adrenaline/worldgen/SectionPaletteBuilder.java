package net.fly.adrenaline.worldgen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.IdentityHashMap;
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
    private static final ThreadLocal<int[]> PACK_IDS = ThreadLocal.withInitial(() -> new int[4096]);

    private SectionPaletteBuilder() {
    }

    public static boolean available() {
        return ACCESS != null;
    }

    public static void unpack(PalettedContainer<BlockState> source, int[] ids, List<BlockState> states) {
        if (ACCESS == null || ids.length != 4096) {
            throw new IllegalArgumentException("Section palette access unavailable");
        }
        try {
            Object data = ACCESS.data.get(source);
            BitStorage storage = (BitStorage) ACCESS.storage.invoke(data);
            @SuppressWarnings("unchecked")
            Palette<BlockState> palette = (Palette<BlockState>) ACCESS.palette.invoke(data);
            storage.unpack(ids);
            if (palette.getSize() <= 256) {
                for (int id = 0; id < palette.getSize(); id++) {
                    states.add(palette.valueFor(id));
                }
            } else {
                IdentityHashMap<BlockState, Integer> remap = new IdentityHashMap<>();
                for (int index = 0; index < ids.length; index++) {
                    BlockState state = palette.valueFor(ids[index]);
                    Integer id = remap.get(state);
                    if (id == null) {
                        id = states.size();
                        states.add(state);
                        remap.put(state, id);
                    }
                    ids[index] = id;
                }
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void commit(PalettedContainer<BlockState> target, List<BlockState> states, int[] ids) {
        commit(target, states, ids, null);
    }

    public static void commit(PalettedContainer<BlockState> target, List<BlockState> states, int[] ids, int[] remap) {
        if (ACCESS == null || ids.length != 4096 || states.isEmpty()) {
            throw new IllegalArgumentException();
        }
        try {
            int requestedBits = Mth.ceillog2(states.size());
            Object configuration = ACCESS.configuration.invoke(PalettedContainer.Strategy.SECTION_STATES, Block.BLOCK_STATE_REGISTRY, requestedBits);
            Palette.Factory factory = (Palette.Factory) ACCESS.factory.invoke(configuration);
            int bits = (int) ACCESS.bits.invoke(configuration);
            Palette<BlockState> palette = factory.create(bits, Block.BLOCK_STATE_REGISTRY, target, states);
            BitStorage storage;
            if (bits == 0) {
                storage = new ZeroBitStorage(4096);
            } else {
                int[] paletteIds = new int[states.size()];
                for (int i = 0; i < paletteIds.length; i++) {
                    paletteIds[i] = palette.idFor(states.get(i));
                }
                if (remap != null) {
                    int[] composed = new int[remap.length];
                    for (int i = 0; i < remap.length; i++) {
                        if (remap[i] >= 0) {
                            composed[i] = paletteIds[remap[i]];
                        }
                    }
                    paletteIds = composed;
                }
                int[] mapped = PACK_IDS.get();
                for (int i = 0; i < ids.length; i++) {
                    mapped[i] = paletteIds[ids[i]];
                }
                storage = new SimpleBitStorage(bits, 4096, mapped);
            }
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
