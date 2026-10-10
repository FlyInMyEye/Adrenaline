package net.fly.adrenaline.worldgen;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.world.level.levelgen.NoiseChunk;

final class CachedDensityOwner {
    private static final ClassValue<VarHandle> OWNERS = new ClassValue<>() {
        @Override protected VarHandle computeValue(Class<?> type) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == NoiseChunk.class && !Modifier.isStatic(field.getModifiers())) {
                    try {
                        field.setAccessible(true);
                        return MethodHandles.lookup().unreflectVarHandle(field);
                    } catch (IllegalAccessException exception) {
                        throw new IllegalArgumentException(exception);
                    }
                }
            }
            throw new IllegalArgumentException("Missing density cache owner");
        }
    };

    private CachedDensityOwner() { }

    static NoiseChunk get(Object cache) {
        return (NoiseChunk) OWNERS.get(cache.getClass()).get(cache);
    }
}
