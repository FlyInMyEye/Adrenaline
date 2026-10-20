package net.fly.adrenaline.worldgen;

import static org.objectweb.asm.Opcodes.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.NativeSurfaceSampler;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextMutable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public final class SurfacePointEvaluator {

    private static final boolean BUFFERED = Boolean.parseBoolean(System.getProperty("adrenaline.bufferedSurface", "true"));
    private static final String EVALUATOR = Type.getInternalName(Evaluator.class);
    private static final String STATE = Type.getDescriptor(BlockState.class);
    private static final String ARGUMENTS = "L" + EVALUATOR + ";";
    private static final MethodHandle FACTORY = createFactory();
    private static final ThreadLocal<Cache> ACTIVE = new ThreadLocal<>();

    private SurfacePointEvaluator() {
    }

    public static Scope suspend() {
        Cache previous = ACTIVE.get();
        ACTIVE.remove();
        return new Scope(previous);
    }

    public static Scope open(BiomeManager manager, SurfaceSystem system) {
        Cache previous = ACTIVE.get();
        Cache cache = previous == null ? (Cache) ((AdrenalineNativeSurfaceProgramsAccess) system).adrenaline$getSurfacePrograms().worker(Cache::new) : new Cache();
        cache.manager = manager;
        cache.activeSystem = system;
        ACTIVE.set(cache);
        return new Scope(previous);
    }

    public static Object context(SurfaceSystem system, RandomState randomState, ChunkAccess chunk, NoiseChunk noiseChunk,
                                 Function<BlockPos, Holder<Biome>> biomeGetter, Registry<Biome> biomes,
                                 WorldGenerationContext generation, SurfaceRules.RuleSource source, Supplier<Object> factory) {
        Cache cache = ACTIVE.get();
        if (cache != null) {
            cache.activeBiomes = biomes;
            cache.activeBiomeGetter = biomeGetter;
            cache.cacheBiomes = false;
            if (cache.resolver == null && AdrenalineConfig.surfaceBiomeOptimizationsEnabled()
                && cache.manager.getClass() == BiomeManager.class
                && cache.manager instanceof AdrenalineBiomeManagerSourceAccess
                && !SurfacePipelineCompatibility.hasForeignHooks(BiomeManager.class)) {
                cache.bindResolver(chunk);
            }
        }
        if (cache == null || !AdrenalineConfig.surfaceContextOptimizationsEnabled() || !SurfacePipelineCompatibility.canReuseContext(system)
            || SurfacePipelineCompatibility.hasForeignHooks(SurfaceRulesContextFactory.contextType())) {
            Object fresh = factory.get();
            if (cache != null && cache.resolver != null && AdrenalineConfig.surfaceBiomeOptimizationsEnabled()
                && !SurfacePipelineCompatibility.hasForeignHooks(system.getClass())
                && !SurfacePipelineCompatibility.hasForeignHooks(fresh.getClass())
                && fresh instanceof AdrenalineMixinSurfaceRulesContextMutable mutable) {
                cache.biomesCache.rebind(chunk, cache.resolver);
                cache.cacheBiomes = true;
                mutable.adrenaline$setBiomeGetter(cache.biomesCache);
            }
            return fresh;
        }
        if (cache.system != system || cache.randomState != randomState || cache.biomes != biomes || cache.source != source
            || cache.minY != generation.getMinGenY() || cache.height != generation.getGenDepth()) {
            boolean supported = cache.knownRules(source);
            Object nextContext = supported ? factory.get() : null;
            cache.system = system;
            cache.randomState = randomState;
            cache.biomes = biomes;
            cache.source = source;
            cache.minY = generation.getMinGenY();
            cache.height = generation.getGenDepth();
            cache.evaluator = null;
            cache.originalRule = null;
            cache.originalHandle = null;
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
            mutable.adrenaline$setBiomeGetter(cache.biomesCache);
            mutable.adrenaline$setLastPreliminarySurfaceCellOrigin(Long.MAX_VALUE);
            mutable.adrenaline$setBiome(null);
        }
        cache.cacheBiomes = AdrenalineConfig.surfaceBiomeOptimizationsEnabled() && cache.manager.getClass() == BiomeManager.class
            && cache.manager instanceof AdrenalineBiomeManagerSourceAccess
            && !SurfacePipelineCompatibility.hasForeignHooks(BiomeManager.class)
            && !SurfacePipelineCompatibility.hasForeignHooks(system.getClass());
        if (cache.cacheBiomes) {
            if (cache.resolver == null) cache.bindResolver(chunk);
            biomeGetter = cache.resolver;
        }
        if (cache.cacheBiomes) {
            cache.biomesCache.rebind(chunk, biomeGetter);
            biomeGetter = cache.biomesCache;
        } else {
            cache.biomesCache.clear();
        }
        ((AdrenalineMixinSurfaceRulesContextMutable) cache.context).adrenaline$setBiomeGetter(biomeGetter);
        if (cache.evaluator != null && cache.evaluator.plan != null) {
            cache.evaluator.plan.biomes(cache.cacheBiomes ? cache.biomesCache : null);
        }
        return cache.context;
    }

    public static Object rule(SurfaceRules.RuleSource source, Object context, WorldGenerationContext generation, Supplier<Object> factory) {
        Cache cache = ACTIVE.get();
        if (cache == null) {
            return factory.get();
        }
        if (cache.context != context || cache.source != source || !cache.supported) {
            Object original = factory.get();
            return nativeRule(cache, source, context, generation, original);
        }
        if (cache.originalRule == null) {
            cache.originalRule = factory.get();
            cache.originalHandle = SurfaceRulesContextFactory.createTryApplyHandle(cache.originalRule);
        }
        return wrap(source, context, generation, cache.originalRule);
    }

    private static Object nativeRule(Cache cache, SurfaceRules.RuleSource source, Object context, WorldGenerationContext generation, Object original) {
        if (FACTORY == null || !(context instanceof AdrenalineMixinSurfaceRulesContextApi api)) {
            return original;
        }
        NativeSurfaceProgram program = AdrenalineConfig.nativeSurfaceRulesEnabled() && NativeSurfaceSampler.available()
            ? ((AdrenalineNativeSurfaceProgramsAccess) cache.activeSystem).adrenaline$getSurfacePrograms()
                .get(source, original, cache.activeBiomes, generation, context) : null;
        SurfaceRulePlan javaPlan = program == null && AdrenalineConfig.surfaceOptimizationsEnabled()
            ? SurfaceRulePlan.compile(source, context, generation, api) : null;
        if (program == null && javaPlan == null) {
            return original;
        }
        if (javaPlan != null) {
            javaPlan.biomes(cache.cacheBiomes ? cache.biomesCache : null);
        }
        NativeSurfaceEvaluator nativePlan = cache.nativePlan;
        Evaluator evaluator = new Evaluator(javaPlan, api, cache.biomesCache, generation.getMinGenY());
        if (program != null) {
            nativePlan.bind(program, api, generation.getMinGenY());
            evaluator.nativePlan = nativePlan;
        }
        evaluator.rebind(SurfaceRulesContextFactory.createTryApplyHandle(original), null, null);
        cache.transientEvaluator = evaluator;
        try {
            return (Object) FACTORY.invokeExact(evaluator);
        } catch (Throwable failure) {
            nativePlan.clear();
            return original;
        }
    }

    public static Object wrap(SurfaceRules.RuleSource source, Object context, WorldGenerationContext generation, Object original) {
        Cache cache = ACTIVE.get();
        if (FACTORY == null || cache == null || cache.context != context || cache.source != source || !cache.supported
            || !(context instanceof AdrenalineMixinSurfaceRulesContextApi contextApi)) {
            return original;
        }
        NativeSurfaceProgram program = AdrenalineConfig.nativeSurfaceRulesEnabled() && NativeSurfaceSampler.available()
            ? ((AdrenalineNativeSurfaceProgramsAccess) cache.activeSystem).adrenaline$getSurfacePrograms().get(source, original, cache.activeBiomes, generation, context) : null;
        if (program != null && !program.contextReusable()) {
            cache.supported = false;
            cache.cacheBiomes = false;
            ((AdrenalineMixinSurfaceRulesContextMutable) context).adrenaline$setBiomeGetter(cache.activeBiomeGetter);
            cache.context = null;
            cache.originalRule = null;
            cache.originalHandle = null;
            cache.evaluator = null;
            cache.biomesCache.clear();
            if (cache.column != null) {
                cache.column.disableBuffering();
            }
            return nativeRule(cache, source, context, generation, original);
        }
        Evaluator evaluator = cache.evaluator;
        if (evaluator == null) {
            evaluator = new Evaluator(null, contextApi, cache.biomesCache, generation.getMinGenY());
            cache.evaluator = evaluator;
        }
        evaluator.nativePlan = null;
        if (program != null) {
            cache.nativePlan.bind(program, contextApi, generation.getMinGenY(), cache.column != null && cache.column.buffered());
            evaluator.nativePlan = cache.nativePlan;
        } else if (AdrenalineConfig.surfaceOptimizationsEnabled()) {
            if (evaluator.plan == null) {
                evaluator.plan = SurfaceRulePlan.compile(source, context, generation, contextApi);
            }
            if (evaluator.plan == null) {
                return original;
            }
            evaluator.plan.biomes(cache.cacheBiomes ? cache.biomesCache : null);
        } else {
            return original;
        }
        evaluator.rebind(cache.originalHandle, cache.column, cache.defaultBlock);
        try {
            cache.adapter = (Object) FACTORY.invokeExact(evaluator);
            return cache.adapter;
        } catch (Throwable failure) {
            throw new IllegalStateException("Could not create surface rule evaluator", failure);
        }
    }

    public static Holder<Biome> biome(BiomeManager manager, BlockPos position) {
        Cache cache = ACTIVE.get();
        return AdrenalineConfig.surfaceBiomeOptimizationsEnabled() && cache != null && cache.manager == manager && cache.resolver != null ? cache.resolver.resolve(position) : null;
    }

    public static BlockColumn column(BlockColumn original, SurfaceSystem system, ChunkAccess chunk, MutableBlockPos position, BlockState defaultBlock, SurfaceRules.RuleSource source) {
        Cache cache = ACTIVE.get();
        if (!AdrenalineConfig.surfaceColumnOptimizationsEnabled()) {
            return original;
        }
        if (!BUFFERED || cache == null || !cache.knownRules(source) || !SectionPaletteBuilder.available()
            || chunk.getClass() != ProtoChunk.class || chunk.getStatus().isOrAfter(ChunkStatus.INITIALIZE_LIGHT)
            || (chunk.getMinBuildHeight() & 15) != 0 || (chunk.getHeight() & 15) != 0
            || chunk.getHeightAccessorForGeneration().getMinBuildHeight() != chunk.getMinBuildHeight()
            || chunk.getHeightAccessorForGeneration().getMaxBuildHeight() != chunk.getMaxBuildHeight()
            || original.getClass().getEnclosingClass() != SurfaceSystem.class
            || SurfacePipelineCompatibility.hasForeignHooks(original.getClass())
            || SurfacePipelineCompatibility.hasForeignHooks(system.getClass())
            || SurfacePipelineCompatibility.hasForeignHooks(SurfaceRulesContextFactory.contextType())
            || SurfacePipelineCompatibility.hasBlockStorageHooks(chunk.getClass())
            || SurfacePipelineCompatibility.hasForeignHooks(LevelChunkSection.class)
            || SurfacePipelineCompatibility.hasForeignHooks(PalettedContainer.class)
            || SurfacePipelineCompatibility.hasForeignHooks(net.minecraft.world.level.levelgen.Heightmap.class)) {
            return liveColumn(original, chunk, position, cache);
        }
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.getClass() != LevelChunkSection.class || section.getStates().getClass() != PalettedContainer.class) {
                return liveColumn(original, chunk, position, cache);
            }
        }
        cache.defaultBlock = defaultBlock;
        cache.column = new FastSurfaceColumn(original, chunk, position, true);
        return cache.column;
    }

    private static BlockColumn liveColumn(BlockColumn original, ChunkAccess chunk, MutableBlockPos position, Cache cache) {
        if (cache == null || chunk.getClass() != ProtoChunk.class
            || original.getClass().getEnclosingClass() != SurfaceSystem.class
            || SurfacePipelineCompatibility.hasForeignHooks(original.getClass())
            || SurfacePipelineCompatibility.hasBlockReadHooks(chunk.getClass())
            || SurfacePipelineCompatibility.hasForeignHooks(net.minecraft.world.level.LevelHeightAccessor.class)) {
            return original;
        }
        cache.column = new FastSurfaceColumn(original, chunk, position, false);
        return cache.column;
    }

    public static Evaluator spanEvaluator(Object rule) {
        Cache cache = ACTIVE.get();
        return cache != null && cache.adapter == rule && cache.evaluator != null && cache.evaluator.blocks != null
            ? cache.evaluator : null;
    }

    public static boolean canCacheBiome(Object context) {
        Cache cache = ACTIVE.get();
        return AdrenalineConfig.surfaceBiomeOptimizationsEnabled() && cache != null && cache.context == context && cache.supported;
    }

    public static final class Evaluator {
        private SurfaceRulePlan plan;
        private NativeSurfaceEvaluator nativePlan;
        private final AdrenalineMixinSurfaceRulesContextApi context;
        private final SurfaceBiomeCache biomes;
        private final int minY;
        private MethodHandle original;
        private int x;
        private int z;
        private int top;
        private int bottom;
        private int stoneTop;
        private int stoneBottom;
        private int water;
        private BlockState result;
        private boolean column;
        private boolean cachedPoint;
        private int pointY;
        private FastSurfaceColumn blocks;
        private BlockState defaultBlock;
        private int spanTop;
        private int spanBottom;

        private Evaluator(SurfaceRulePlan plan, AdrenalineMixinSurfaceRulesContextApi context, SurfaceBiomeCache biomes, int minY) {
            this.plan = plan;
            this.context = context;
            this.biomes = biomes;
            this.minY = minY;
        }

        private void rebind(MethodHandle original, FastSurfaceColumn blocks, BlockState defaultBlock) {
            this.original = original;
            this.blocks = AdrenalineConfig.surfaceSpanOptimizationsEnabled() && blocks != null && blocks.buffered() ? blocks : null;
            this.defaultBlock = defaultBlock;
            this.spanTop = 0;
            this.spanBottom = 0;
            this.column = false;
            this.cachedPoint = false;
            this.bottom = Integer.MAX_VALUE;
        }

        public boolean shouldUpdate(int above, int below, int water, int x, int y, int z) {
            this.spanTop = y;
            this.spanBottom = y;
            return this.blocks == null || this.blocks.getBlock(y) == this.defaultBlock
                && !this.skipContextUpdate(above, below, water, x, y, z);
        }

        public int spanDepth(int above) {
            return above + this.spanTop - this.spanBottom;
        }

        public int spanY(int y) {
            return this.spanTop == y ? this.spanBottom : y;
        }

        public BlockState tryApply(int x, int y, int z) {
            BlockState state = this.apply(x, y, z);
            if (this.blocks == null) {
                return state;
            }
            this.spanTop = y;
            this.spanBottom = y;
            if (this.x != x || this.z != z || this.top < y || this.bottom >= y || this.result != state) {
                return state;
            }
            this.spanBottom = this.blocks.defaultBottom(y, this.bottom, this.defaultBlock);
            if (this.spanBottom == y) {
                return state;
            }
            if (state != null) {
                this.blocks.fill(this.spanBottom, y, state);
            }
            return null;
        }

        private boolean skipContextUpdate(int above, int below, int water, int x, int y, int z) {
            this.cachedPoint = this.column && this.x == x && this.z == z && y <= this.top && y >= this.bottom
                && y + above == this.stoneTop && y - below == this.stoneBottom && water == this.water;
            this.pointY = y;
            return this.cachedPoint;
        }

        public BlockState apply(int x, int y, int z) {
            if (this.cachedPoint && this.x == x && this.z == z && this.pointY == y) {
                return this.result;
            }
            int above = this.context.adrenaline$getStoneDepthAbove();
            int below = this.context.adrenaline$getStoneDepthBelow();
            int water = this.context.adrenaline$getWaterHeight();
            if (this.blocks != null && this.column && this.x == x && this.z == z && y <= this.top && y >= this.bottom
                && y + above == this.stoneTop && y - below == this.stoneBottom && water == this.water) {
                return this.result;
            }
            if (!this.column || this.x != x || this.z != z) {
                this.biomes.column(x, z);
                this.column = true;
            }
            this.x = x;
            this.z = z;
            this.top = y;
            this.bottom = y;
            this.stoneTop = y + above;
            this.stoneBottom = y - below;
            this.water = water;
            if (this.nativePlan != null) {
                int index = this.nativePlan.evaluate(x, y, z);
                if (this.nativePlan.unsafe() && this.blocks != null) {
                    this.blocks.disableBuffering();
                    this.blocks = null;
                }
                if (index >= -1) {
                    this.result = this.nativePlan.state(index);
                    if (this.blocks != null) {
                        this.bottom = this.nativePlan.bottom(y);
                        if (this.nativePlan.usesBiome()) {
                            Cache cache = ACTIVE.get();
                            this.bottom = cache != null && cache.cacheBiomes ? Math.max(this.bottom, this.biomes.certifiedBottom(y, this.bottom)) : y;
                        }
                    }
                    return this.result;
                }
            } else if (this.plan != null) {
                BlockState value = this.plan.evaluate(x, y, z, above, below, water, Math.max(this.minY, y - below + 1));
                if (!this.plan.uncertain()) {
                    this.bottom = this.plan.bottom();
                    this.result = value;
                    return value;
                }
            }
            try {
                this.result = (BlockState) this.original.invokeExact(x, y, z);
                return this.result;
            } catch (Throwable failure) {
                throw new IllegalStateException("Surface rule evaluation failed", failure);
            }
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
                            Evaluator.class))
                            .asType(MethodType.methodType(Object.class, Evaluator.class));
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
        writer.visitField(ACC_PRIVATE | ACC_FINAL, "evaluator", ARGUMENTS, null, null).visitEnd();
        MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "(" + ARGUMENTS + ")V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        constructor.visitVarInsn(ALOAD, 0);
        constructor.visitVarInsn(ALOAD, 1);
        constructor.visitFieldInsn(PUTFIELD, name, "evaluator", ARGUMENTS);
        constructor.visitInsn(RETURN);
        constructor.visitMaxs(0, 0);
        constructor.visitEnd();
        MethodVisitor apply = writer.visitMethod(ACC_PUBLIC, methodName, "(III)" + STATE, null, null);
        apply.visitCode();
        apply.visitVarInsn(ALOAD, 0);
        apply.visitFieldInsn(GETFIELD, name, "evaluator", ARGUMENTS);
        for (int i = 1; i <= 3; i++) {
            apply.visitVarInsn(ILOAD, i);
        }
        apply.visitMethodInsn(INVOKEVIRTUAL, EVALUATOR, "tryApply", "(III)" + STATE, false);
        apply.visitInsn(ARETURN);
        apply.visitMaxs(0, 0);
        apply.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static final class Cache {
        private SurfaceSystem activeSystem;
        private Registry<Biome> activeBiomes;
        private Function<BlockPos, Holder<Biome>> activeBiomeGetter;
        private final NativeSurfaceEvaluator nativePlan = new NativeSurfaceEvaluator();
        private Evaluator transientEvaluator;
        private SurfaceSystem system;
        private SurfaceRules.RuleSource checkedSource;
        private boolean checkedRules;
        private BiomeManager manager;
        private SurfaceBiomeResolver resolver;
        private SurfaceBiomeResolver resolverPool;
        private boolean cacheBiomes;
        private FastSurfaceColumn column;
        private BlockState defaultBlock;
        private RandomState randomState;
        private Registry<Biome> biomes;
        private SurfaceRules.RuleSource source;
        private int minY;
        private int height;
        private Object context;
        private final SurfaceBiomeCache biomesCache = new SurfaceBiomeCache();
        private Evaluator evaluator;
        private Object originalRule;
        private Object adapter;
        private MethodHandle originalHandle;
        private boolean supported;

        private void bindResolver(ChunkAccess chunk) {
            if (this.resolverPool == null) {
                this.resolverPool = new SurfaceBiomeResolver(this.manager, chunk);
            } else {
                this.resolverPool.rebind(this.manager, chunk);
            }
            this.resolver = this.resolverPool;
        }

        private boolean knownRules(SurfaceRules.RuleSource source) {
            if (this.checkedSource != source) {
                this.checkedSource = source;
                this.checkedRules = SurfaceRulePlan.supportsBufferedWrites(source);
            }
            return this.checkedRules;
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Cache previous;

        private Scope(Cache previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            Cache current = ACTIVE.get();
            try {
                if (current != null && current.column != null) {
                    current.column.finish();
                }
            } finally {
                if (current != null) {
                    current.nativePlan.clear();
                    if (current.transientEvaluator != null) {
                        current.transientEvaluator.nativePlan = null;
                    }
                    current.transientEvaluator = null;
                    current.activeSystem = null;
                    current.activeBiomes = null;
                    current.activeBiomeGetter = null;
                    current.column = null;
                    current.adapter = null;
                    current.manager = null;
                    if (current.resolver != null) {
                        current.resolver.clear();
                    }
                    current.resolver = null;
                    if (current.context instanceof AdrenalineMixinSurfaceRulesContextMutable mutable) {
                        mutable.adrenaline$setChunk(null);
                        mutable.adrenaline$setNoiseChunk(null);
                        mutable.adrenaline$setBiomeGetter(null);
                        mutable.adrenaline$setBiome(null);
                    }
                    current.biomesCache.clear();
                    if (current.evaluator != null) {
                        current.evaluator.blocks = null;
                        current.evaluator.original = null;
                        current.evaluator.result = null;
                    }
                }
                if (this.previous == null) {
                    ACTIVE.remove();
                } else {
                    ACTIVE.set(this.previous);
                }
            }
        }
    }
}
