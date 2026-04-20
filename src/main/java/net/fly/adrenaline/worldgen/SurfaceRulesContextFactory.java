package net.fly.adrenaline.worldgen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;

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
    private static final Method TRY_APPLY_METHOD;

    static {
        try {
            Class<?> contextType = Class.forName("net.minecraft.world.level.levelgen.SurfaceRules$Context");
            Class<?> surfaceRuleType = Class.forName("net.minecraft.world.level.levelgen.SurfaceRules$SurfaceRule");
            CONSTRUCTOR = contextType.getDeclaredConstructor(SurfaceSystem.class, RandomState.class, ChunkAccess.class, NoiseChunk.class, Function.class, Registry.class, WorldGenerationContext.class);
            CONSTRUCTOR.setAccessible(true);
            TRY_APPLY_METHOD = findTryApplyMethod(surfaceRuleType);
            TRY_APPLY_METHOD.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private SurfaceRulesContextFactory() {
    }

    public static Object create(SurfaceSystem system, RandomState randomState, ChunkAccess chunk, NoiseChunk noiseChunk, Function<BlockPos, Holder<Biome>> biomeGetter, Registry<Biome> biomeRegistry, WorldGenerationContext context) {
        try {
            return CONSTRUCTOR.newInstance(system, randomState, chunk, noiseChunk, biomeGetter, biomeRegistry, context);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static Object apply(SurfaceRules.RuleSource ruleSource, Object context) {
        return applyUnchecked(ruleSource, context);
    }

    public static SurfaceRulePipeline createPipeline(SurfaceSystem system, RandomState randomState, ChunkAccess chunk, NoiseChunk noiseChunk, Function<BlockPos, Holder<Biome>> biomeGetter, Registry<Biome> biomeRegistry, WorldGenerationContext context, SurfaceRules.RuleSource ruleSource) {
        Object surfaceContext = create(system, randomState, chunk, noiseChunk, biomeGetter, biomeRegistry, context);
        Object surfaceRule = apply(ruleSource, surfaceContext);
        return new SurfaceRulePipeline(surfaceRule, surfaceContext);
    }

    public static BlockState tryApply(Object surfaceRule, int x, int y, int z) {
        try {
            return (BlockState) TRY_APPLY_METHOD.invoke(surfaceRule, x, y, z);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object applyUnchecked(Function function, Object context) {
        return function.apply(context);
    }
}
