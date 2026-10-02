package net.fly.adrenaline.worldgen;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public final class DensityFunctionOperation {

    private static final ClassValue<Field> OPERATION_FIELDS = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            for (Field field : type.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.getType().isEnum()) {
                    field.setAccessible(true);
                    return field;
                }
            }
            throw new IllegalArgumentException("Missing density function operation in " + type.getName());
        }
    };

    private DensityFunctionOperation() {
    }

    public static int ordinal(Object function) throws IllegalAccessException {
        return ((Enum<?>) OPERATION_FIELDS.get(function.getClass()).get(function)).ordinal();
    }
}
