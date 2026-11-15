package net.fly.adrenaline.worldgen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

public final class SurfaceRulesContextFactory {

    private static final Constructor<?> CONSTRUCTOR;
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final ClassValue<MethodHandle> CONDITION_TESTS = new ClassValue<>() {
        @Override
        protected MethodHandle computeValue(Class<?> type) {
            try {
                Method method = java.util.Arrays.stream(type.getMethods())
                    .filter(candidate -> candidate.getParameterCount() == 0 && candidate.getReturnType() == boolean.class)
                    .findFirst().orElseThrow(() -> new NoSuchMethodException("Surface condition test method"));
                method.setAccessible(true);
                return LOOKUP.unreflect(method).asType(MethodType.methodType(boolean.class, Object.class));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not access surface condition", exception);
            }
        }
    };

    static {
        try {
            CONSTRUCTOR = findContextConstructor();
            CONSTRUCTOR.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private SurfaceRulesContextFactory() {
    }

    public static Class<?> contextType() {
        return CONSTRUCTOR.getDeclaringClass();
    }

    public static Object create(SurfaceSystem system, RandomState randomState, ChunkAccess chunk, NoiseChunk noiseChunk, Function<BlockPos, Holder<Biome>> biomeGetter, Registry<Biome> biomeRegistry, WorldGenerationContext context) {
        try {
            return CONSTRUCTOR.newInstance(system, randomState, chunk, noiseChunk, biomeGetter, biomeRegistry, context);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static MethodHandle createTryApplyHandle(Object surfaceRule) {
        try {
            Method method = findTryApplyMethod(surfaceRule.getClass());
            method.setAccessible(true);
            return LOOKUP.unreflect(method).bindTo(surfaceRule);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static Object apply(SurfaceRules.RuleSource ruleSource, Object context) {
        return applyUnchecked(ruleSource, context);
    }

    public static Object applyCondition(SurfaceRules.ConditionSource source, Object context) {
        return applyUnchecked(source, context);
    }

    public static BooleanSupplier createCondition(SurfaceRules.ConditionSource source, Object context) throws ReflectiveOperationException {
        Object condition = applyUnchecked(source, context);
        MethodHandle test = CONDITION_TESTS.get(condition.getClass()).bindTo(condition);
        return () -> {
            try {
                return (boolean) test.invokeExact();
            } catch (Throwable throwable) {
                throw new IllegalStateException("Surface condition evaluation failed", throwable);
            }
        };
    }

    private static Method findTryApplyMethod(Class<?> surfaceRuleType) throws NoSuchMethodException {
        try {
            return surfaceRuleType.getDeclaredMethod("tryApply", int.class, int.class, int.class);
        } catch (NoSuchMethodException ignored) {
        }

        for (Method method : surfaceRuleType.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length == 3
                && parameterTypes[0] == int.class
                && parameterTypes[1] == int.class
                && parameterTypes[2] == int.class
                && method.getReturnType() == BlockState.class) {
                return method;
            }
        }

        throw new NoSuchMethodException("Could not find SurfaceRules.SurfaceRule tryApply method");
    }

    private static Constructor<?> findContextConstructor() throws NoSuchMethodException {
        for (Class<?> nestedType : SurfaceRules.class.getDeclaredClasses()) {
            try {
                return nestedType.getDeclaredConstructor(
                    SurfaceSystem.class,
                    RandomState.class,
                    ChunkAccess.class,
                    NoiseChunk.class,
                    Function.class,
                    Registry.class,
                    WorldGenerationContext.class
                );
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("Could not find SurfaceRules context constructor");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object applyUnchecked(Function function, Object context) {
        return function.apply(context);
    }

}
