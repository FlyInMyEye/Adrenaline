package net.fly.adrenaline.worldgen;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.levelgen.SurfaceRules;

final class SurfaceRuleSources {

    private static final Field[] UNSUPPORTED = new Field[0];
    private static final Class<?> LIST_N = List.of().getClass();
    private static final Class<?> LIST_12 = List.of(true).getClass();
    private static final Class<?> MAP_N = Map.of().getClass();
    private static final Class<?> MAP_1 = Map.of(true, true).getClass();
    private static final ClassValue<Field[]> FIELDS = new ClassValue<>() {
        @Override
        protected Field[] computeValue(Class<?> type) {
            var fields = new ArrayList<Field>();
            try {
                for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                    for (Field field : current.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers())) continue;
                        if (!Modifier.isFinal(field.getModifiers())) return UNSUPPORTED;
                        field.setAccessible(true);
                        fields.add(field);
                    }
                }
                return fields.toArray(Field[]::new);
            } catch (RuntimeException exception) {
                return UNSUPPORTED;
            }
        }
    };

    private SurfaceRuleSources() { }

    static boolean equivalent(SurfaceRules.RuleSource first, SurfaceRules.RuleSource second) {
        try {
            return equivalent(first, second, 0);
        } catch (IllegalAccessException exception) {
            return false;
        }
    }

    private static boolean equivalent(Object first, Object second, int depth) throws IllegalAccessException {
        if (first == second) return true;
        if (first == null || second == null || first.getClass() != second.getClass() || depth > 128) return false;
        Class<?> type = first.getClass();
        if (type == String.class || type == Boolean.class || type == Character.class || type == Byte.class
            || type == Short.class || type == Integer.class || type == Long.class || type == Float.class || type == Double.class) {
            return first.equals(second);
        }
        if (type.getEnclosingClass() == SurfaceRules.class && first.equals(second)) return true;
        if (first instanceof List<?> left && (first instanceof ImmutableList<?> || type == LIST_N || type == LIST_12
            || type.getName().equals("java.util.Arrays$ArrayList"))) {
            List<?> right = (List<?>) second;
            if (left.size() != right.size()) return false;
            for (int i = 0; i < left.size(); i++) {
                if (!equivalent(left.get(i), right.get(i), depth + 1)) return false;
            }
            return true;
        }
        if (first instanceof Map<?, ?> left && (first instanceof ImmutableMap<?, ?> || type == MAP_N || type == MAP_1)) {
            Map<?, ?> right = (Map<?, ?>) second;
            if (!left.keySet().equals(right.keySet())) return false;
            for (var entry : left.entrySet()) {
                if (!equivalent(entry.getValue(), right.get(entry.getKey()), depth + 1)) return false;
            }
            return true;
        }
        if (!(first instanceof SurfaceRules.RuleSource) && !(first instanceof SurfaceRules.ConditionSource)) return false;
        Field[] fields = FIELDS.get(type);
        if (fields == UNSUPPORTED) return false;
        for (Field field : fields) {
            if (!equivalent(field.get(first), field.get(second), depth + 1)) return false;
        }
        return true;
    }
}
