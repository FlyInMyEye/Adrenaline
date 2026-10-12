package net.fly.adrenaline.worldgen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextMutable;
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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class SurfacePointEvaluator implements Opcodes {

    private static final String PLAN = Type.getInternalName(SurfaceRulePlan.class);
    private static final String CONTEXT = Type.getInternalName(AdrenalineMixinSurfaceRulesContextApi.class);
    private static final String HANDLE = Type.getInternalName(MethodHandle.class);
    private static final String STATE = Type.getDescriptor(BlockState.class);
    private static final String ARGUMENTS = "L" + PLAN + ";L" + HANDLE + ";L" + CONTEXT + ";";
    private static final MethodHandle FACTORY = createFactory();
    private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);
    private static final ThreadLocal<Cache> ACTIVE = new ThreadLocal<>();

    private SurfacePointEvaluator() {
    }

    public static Scope open() {
        Cache previous = ACTIVE.get();
        ACTIVE.set(previous == null ? CACHE.get() : new Cache());
        return new Scope(previous);
    }

    public static Object context(SurfaceSystem system, RandomState randomState, ChunkAccess chunk, NoiseChunk noiseChunk,
                                 Function<BlockPos, Holder<Biome>> biomeGetter, Registry<Biome> biomes,
                                 WorldGenerationContext generation, SurfaceRules.RuleSource source, Supplier<Object> factory) {
        Cache cache = ACTIVE.get();
        if (cache == null || !SurfacePipelineCompatibility.canReuseContext(system)
            || SurfacePipelineCompatibility.hasForeignHooks(SurfaceRulesContextFactory.contextType())) {
            return factory.get();
        }
        if (cache.system != system || cache.randomState != randomState || cache.biomes != biomes || cache.source != source
            || cache.minY != generation.getMinGenY() || cache.height != generation.getGenDepth()) {
            boolean supported = SurfaceRulePlan.supportsBufferedWrites(source);
            Object nextContext = supported ? factory.get() : null;
            cache.system = system;
            cache.randomState = randomState;
            cache.biomes = biomes;
            cache.source = source;
            cache.minY = generation.getMinGenY();
            cache.height = generation.getGenDepth();
            cache.plan = null;
            cache.supported = supported;
            cache.context = nextContext;
            if (!cache.supported) {
                return factory.get();
            }
        } else {
            if (!cache.supported) {
                return factory.get();
            }
            AdrenalineMixinSurfaceRulesContextMutable mutable = (AdrenalineMixinSurfaceRulesContextMutable) cache.context;
            mutable.adrenaline$setChunk(chunk);
            mutable.adrenaline$setNoiseChunk(noiseChunk);
            mutable.adrenaline$setBiomeGetter(biomeGetter);
            mutable.adrenaline$setLastPreliminarySurfaceCellOrigin(Long.MAX_VALUE);
            mutable.adrenaline$setBiome(null);
        }
        return cache.context;
    }

    public static Object wrap(SurfaceRules.RuleSource source, Object context, WorldGenerationContext generation, Object original) {
        Cache cache = ACTIVE.get();
        if (FACTORY == null || cache == null || cache.context != context || cache.source != source || !cache.supported
            || !(context instanceof AdrenalineMixinSurfaceRulesContextApi contextApi)) {
            return original;
        }
        SurfaceRulePlan plan = cache.plan;
        if (plan == null) {
            plan = SurfaceRulePlan.compile(source, context, generation, contextApi);
            cache.plan = plan;
        }
        if (plan == null) {
            return original;
        }
        MethodHandle fallback = SurfaceRulesContextFactory.createTryApplyHandle(original);
        try {
            return (Object) FACTORY.invokeExact(plan, fallback, contextApi);
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not create surface rule evaluator", failure);
        }
    }

    public static BlockState apply(SurfaceRulePlan plan, MethodHandle original, AdrenalineMixinSurfaceRulesContextApi context,
                                   int x, int y, int z) {
        BlockState result = plan.evaluate(x, y, z, context.adrenaline$getStoneDepthAbove(),
            context.adrenaline$getStoneDepthBelow(), context.adrenaline$getWaterHeight(), y);
        if (!plan.uncertain()) {
            return result;
        }
        try {
            return (BlockState) original.invokeExact(x, y, z);
        } catch (Throwable failure) {
            throw new IllegalStateException("Surface rule evaluation failed", failure);
        }
    }

    private static MethodHandle createFactory() {
        try {
            for (Class<?> type : SurfaceRules.class.getDeclaredClasses()) {
                if (!type.isInterface()) {
                    continue;
                }
                for (Method method : type.getDeclaredMethods()) {
                    if (method.getReturnType() == BlockState.class
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class, int.class, int.class})) {
                        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
                        Class<?> adapter = lookup.defineClass(adapter(type, method.getName()));
                        return lookup.findConstructor(adapter, MethodType.methodType(void.class,
                            SurfaceRulePlan.class, MethodHandle.class, AdrenalineMixinSurfaceRulesContextApi.class))
                            .asType(MethodType.methodType(Object.class, SurfaceRulePlan.class, MethodHandle.class,
                                AdrenalineMixinSurfaceRulesContextApi.class));
                    }
                }
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static byte[] adapter(Class<?> ruleType, String methodName) {
        String name = Type.getInternalName(ruleType).substring(0, Type.getInternalName(ruleType).lastIndexOf('/') + 1)
            + "AdrenalineSurfacePointRule";
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V17, ACC_PUBLIC | ACC_FINAL, name, null, "java/lang/Object", new String[]{Type.getInternalName(ruleType)});
        String[] fields = {"plan", "original", "context"};
        String[] descriptors = {"L" + PLAN + ";", "L" + HANDLE + ";", "L" + CONTEXT + ";"};
        for (int i = 0; i < fields.length; i++) {
            writer.visitField(ACC_PRIVATE | ACC_FINAL, fields[i], descriptors[i], null, null).visitEnd();
        }
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "(" + ARGUMENTS + ")V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        for (int i = 0; i < fields.length; i++) {
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitVarInsn(ALOAD, i + 1);
            constructor.visitFieldInsn(PUTFIELD, name, fields[i], descriptors[i]);
        }
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor apply = writer.visitMethod(ACC_PUBLIC, methodName, "(III)" + STATE, null, null);
        apply.visitCode();
        for (int i = 0; i < fields.length; i++) {
            apply.visitVarInsn(ALOAD, 0);
            apply.visitFieldInsn(GETFIELD, name, fields[i], descriptors[i]);
        }
        for (int i = 1; i <= 3; i++) {
            apply.visitVarInsn(ILOAD, i);
        }
        apply.visitMethodInsn(INVOKESTATIC, Type.getInternalName(SurfacePointEvaluator.class), "apply",
            "(" + ARGUMENTS + "III)" + STATE, false);
        apply.visitInsn(ARETURN);
        apply.visitMaxs(0, 0);
        apply.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class Cache {
        private SurfaceSystem system;
        private RandomState randomState;
        private Registry<Biome> biomes;
        private SurfaceRules.RuleSource source;
        private int minY;
        private int height;
        private Object context;
        private SurfaceRulePlan plan;
        private boolean supported;
    }

    public static final class Scope implements AutoCloseable {
        private final Cache previous;

        private Scope(Cache previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            Cache current = ACTIVE.get();
            if (current != null && current.context instanceof AdrenalineMixinSurfaceRulesContextMutable mutable) {
                mutable.adrenaline$setChunk(null);
                mutable.adrenaline$setNoiseChunk(null);
                mutable.adrenaline$setBiomeGetter(null);
                mutable.adrenaline$setBiome(null);
            }
            if (this.previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(this.previous);
            }
        }
    }
}
