package net.fly.adrenaline.worldgen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinLegacyPositionalFactoryAccess;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceBiomeConditionSource;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceSystemAccess;
import net.fly.adrenaline.natives.PerlinNativeSampler;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinXoroshiroPositionalFactoryAccess;
import net.fly.adrenaline.natives.NativeSurfaceSampler;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

public final class NativeSurfaceProgram {

    public static final int FALLBACK = -2;
    public static final int MINIMUM = -4;
    public static final int SECONDARY = -5;
    public static final int BIOME = -6;
    public static final int STEEP = -7;
    public static final int TEMPERATURE = -8;
    public static final int BIOME_INPUT = 1;
    public static final int MINIMUM_INPUT = 2;
    public static final int SECONDARY_INPUT = 4;
    public static final int STEEP_INPUT = 8;
    public static final int TEMPERATURE_INPUT = 16;
    private static final Class<?> XOROSHIRO_FACTORY = new XoroshiroRandomSource(0L).forkPositional().getClass();
    private static final Class<?> LEGACY_FACTORY = new LegacyRandomSource(0L).forkPositional().getClass();
    private static final Class<?> DIRECT_HOLDER = Holder.direct(new Object()).getClass();
    private static final int MAX_NODES = 65536;
    private static final Class<?> RUNTIME_RULE = runtimeRuleType();
    private static final Class<?> BLOCK = blockSource();
    private static final Class<?> SEQUENCE = sourceRecord(List.class);
    private static final Class<?> TEST = sourceRecord(SurfaceRules.ConditionSource.class, SurfaceRules.RuleSource.class);
    private static final Class<?> BIOME_SOURCE = biomeSource();
    private static final Class<?> NOT = sourceRecord(SurfaceRules.ConditionSource.class);
    private static final Class<?> STONE = SurfaceRules.ON_FLOOR.getClass();
    private static final Class<?> Y = sourceRecord(VerticalAnchor.class, int.class, boolean.class);
    private static final Class<?> WATER = sourceRecord(int.class, int.class, boolean.class);
    private static final Class<?> NOISE = sourceRecord(ResourceKey.class, double.class, double.class);
    private static final Class<?> GRADIENT = sourceRecord(net.minecraft.resources.ResourceLocation.class, VerticalAnchor.class, VerticalAnchor.class);
    private static final ClassValue<Method[]> ACCESSORS = new ClassValue<>() {
        @Override
        protected Method[] computeValue(Class<?> type) {
            var components = type.getRecordComponents();
            if (components == null) {
                throw new IllegalArgumentException("Surface rule is not a record");
            }
            Method[] accessors = new Method[components.length];
            for (int i = 0; i < components.length; i++) {
                accessors[i] = components[i].getAccessor();
                accessors[i].setAccessible(true);
            }
            return accessors;
        }
    };
    private static final Class<?> RUNTIME_BLOCK = BLOCK.getRecordComponents()[1].getType();
    private static final Class<?> RUNTIME_SEQUENCE = runtimeSequence();
    private static final Class<?> RUNTIME_TEST = runtimeTest();
    private static final Class<?> RUNTIME_CONDITION = RUNTIME_TEST.getRecordComponents()[0].getType();
    private static final Class<?> RUNTIME_NOT = runtimeNot();
    private final ByteBuffer bytecode;
    private final long address;
    private final boolean contextReusable;
    private final boolean steep;
    private final int firstInput;
    private final java.lang.invoke.MethodHandle temperatureTransform;
    private final PerlinNativeSampler.Data[] noiseData;
    private final IdentityHashMap<Biome, Integer> climateIds;
    private final BlockState[] states;
    private final IdentityHashMap<ResourceKey<Biome>, Integer> biomeIds;

    private NativeSurfaceProgram(Builder builder, int[] entries) {
        int offset = (64 + entries.length * 4 + 7) & ~7;
        NativeSurfaceData data = builder.data;
        int noiseOffset = offset + builder.nodes.size() * 32 + builder.randoms.size() * 32;
        int simplexOffset = noiseOffset + data.noises.size() * 64;
        int climateOffset = simplexOffset + data.simplexes.size() * 32;
        int indexOffset = climateOffset + data.climates.size() * 24;
        int simplexDataOffset = (indexOffset + data.indices.size() * 4 + 7) & ~7;
        this.bytecode = ByteBuffer.allocateDirect(simplexDataOffset + data.simplexBytes()).order(ByteOrder.nativeOrder());
        this.bytecode.putInt(7).putInt(entries.length).putInt(builder.nodes.size()).putInt(offset)
            .putInt(builder.randoms.size()).putInt(data.noises.size()).putInt(data.simplexes.size()).putInt(data.climates.size())
            .putInt(noiseOffset).putInt(simplexOffset).putInt(climateOffset).putInt(indexOffset)
            .putInt(data.indices.size()).putInt(simplexDataOffset).putInt(data.simplexBytes()).putInt(0);
        for (int entry : entries) {
            this.bytecode.putInt(entry);
        }
        this.bytecode.position(offset);
        for (Node node : builder.nodes) {
            this.bytecode.putInt(node.op).putInt(node.a).putInt(node.b).putInt(node.c).putInt(node.d)
                .putInt(node.yes).putInt(node.no).putInt(0);
        }
        for (RandomConfig config : builder.randoms) {
            this.bytecode.putLong(config.low).putLong(config.high).putInt(config.type).putInt(0).putLong(0L);
        }
        data.write(this.bytecode, noiseOffset, simplexOffset, climateOffset, indexOffset, simplexDataOffset);
        this.steep = builder.steep;
        Node first = builder.nodes.get(entries[0]);
        this.firstInput = switch (first.op) {
            case 0 -> first.a <= MINIMUM && first.a >= TEMPERATURE ? first.a : 0;
            case 1 -> first.c == 0 ? 0 : SECONDARY;
            case 5 -> MINIMUM;
            case 7 -> BIOME;
            case 11 -> TEMPERATURE;
            case 13 -> STEEP;
            default -> 0;
        };
        this.temperatureTransform = data.temperatureTransform;
        this.noiseData = data.noises.stream().flatMap(value -> java.util.stream.Stream.of(value.first(), value.second())).toArray(PerlinNativeSampler.Data[]::new);
        this.climateIds = data.climateIds;
        this.bytecode.clear();
        this.address = NativeSurfaceSampler.address(this.bytecode);
        this.contextReusable = builder.contextReusable && this.temperatureTransform == null;
        this.states = builder.palette.toArray(BlockState[]::new);
        this.biomeIds = builder.biomeIds;
    }

    public static NativeSurfaceProgram compile(SurfaceRules.RuleSource source, Object original, Registry<Biome> registry, WorldGenerationContext generation) {
        return compile(source, original, registry, generation, null);
    }

    public static NativeSurfaceProgram compile(SurfaceRules.RuleSource source, Object original, Registry<Biome> registry, WorldGenerationContext generation, Object context) {
        try {
            Builder builder = new Builder(generation, registry, context);
            List<ResourceKey<Biome>> keys = new ArrayList<>();
            keys.add(null);
            keys.add(null);
            for (var holder : registry.holders().toList()) {
                builder.biomeIds.put(holder.key(), keys.size());
                keys.add(holder.key());
            }
            if (keys.size() > 16384) {
                return null;
            }
            int[] entries = new int[keys.size()];
            int miss = builder.node(new Node(0, -1, 0, 0, 0, 0, 0));
            for (int biome = 0; biome < entries.length; biome++) {
                builder.memo.clear();
                entries[biome] = builder.rule(source, original, keys.get(biome), biome == 0, miss, 0);
            }
            return new NativeSurfaceProgram(builder, entries);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    public boolean needsSteep() {
        return this.steep;
    }

    public int firstInput() {
        return this.firstInput;
    }

    public net.minecraft.core.BlockPos temperaturePosition(Holder<Biome> biome, net.minecraft.core.BlockPos.MutableBlockPos position) {
        if (this.temperatureTransform == null) return position;
        try {
            return (net.minecraft.core.BlockPos) this.temperatureTransform.invokeExact(biome.value(), (net.minecraft.core.BlockPos) position);
        } catch (Throwable exception) {
            throw new IllegalStateException("Surface temperature position transform failed", exception);
        }
    }

    public int temperatureId(Holder<Biome> holder) {
        if (holder.getClass() != Holder.Reference.class && holder.getClass() != DIRECT_HOLDER) return -1;
        return this.climateIds.getOrDefault(holder.value(), -1);
    }

    public boolean contextReusable() {
        return this.contextReusable;
    }

    public long address() {
        return this.address;
    }

    public int length() {
        return this.bytecode.capacity();
    }

    public BlockState state(int index) {
        return index == -1 ? null : this.states[index];
    }

    public int biomeId(Holder<Biome> holder) {
        if (holder.getClass() != Holder.Reference.class && holder.getClass() != DIRECT_HOLDER) {
            return -1;
        }
        var key = holder.unwrapKey();
        return key.isEmpty() ? 1 : this.biomeIds.getOrDefault(key.get(), -1);
    }

    private static Object captured(Object object, Class<?> type) throws IllegalAccessException {
        for (Class<?> current = object.getClass(); current != null; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return field.get(object);
                }
            }
        }
        return null;
    }

    private static Object component(Object record, int index) throws ReflectiveOperationException {
        return ACCESSORS.get(record.getClass())[index].invoke(record);
    }

    private static Class<?> sourceRecord(Class<?>... fields) {
        for (Class<?> type : SurfaceRules.class.getDeclaredClasses()) {
            if (!SurfaceRules.RuleSource.class.isAssignableFrom(type) && !SurfaceRules.ConditionSource.class.isAssignableFrom(type)) continue;
            var components = type.getRecordComponents();
            if (components != null && components.length == fields.length) {
                boolean matches = true;
                for (int i = 0; i < fields.length; i++) matches &= components[i].getType() == fields[i];
                if (matches) return type;
            }
        }
        throw new IllegalStateException("Surface source record type");
    }

    private static Class<?> runtimeRuleType() {
        for (Class<?> type : SurfaceRules.class.getDeclaredClasses()) {
            if (!type.isInterface()) continue;
            for (Method method : type.getDeclaredMethods()) {
                if (method.getReturnType() == BlockState.class && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class, int.class, int.class})) return type;
            }
        }
        throw new IllegalStateException("Surface rule interface");
    }

    private static Class<?> blockSource() {
        for (Class<?> type : SurfaceRules.class.getDeclaredClasses()) {
            var components = type.getRecordComponents();
            if (SurfaceRules.RuleSource.class.isAssignableFrom(type) && components != null && components.length == 2
                && components[0].getType() == BlockState.class && RUNTIME_RULE.isAssignableFrom(components[1].getType())) return type;
        }
        throw new IllegalStateException("Surface block source type");
    }

    private static Class<?> biomeSource() {
        for (Class<?> type : SurfaceRules.class.getDeclaredClasses()) {
            if (!SurfaceRules.ConditionSource.class.isAssignableFrom(type)) continue;
            for (Field field : type.getDeclaredFields()) if (field.getType() == java.util.function.Predicate.class) return type;
        }
        throw new IllegalStateException("Surface biome source type");
    }

    private static Class<?> runtimeSequence() {
        for (Class<?> type : SurfaceRules.class.getDeclaredClasses()) {
            var components = type.getRecordComponents();
            if (components != null && components.length == 1
                && components[0].getGenericType() instanceof ParameterizedType list
                && list.getRawType() == List.class && list.getActualTypeArguments()[0] == RUNTIME_RULE) {
                return type;
            }
        }
        throw new IllegalStateException("Surface sequence rule type");
    }

    private static Class<?> runtimeTest() {
        for (Class<?> type : SurfaceRules.class.getDeclaredClasses()) {
            var components = type.getRecordComponents();
            if (components != null && components.length == 2 && components[1].getType() == RUNTIME_RULE
                && components[0].getType().isInterface()) {
                return type;
            }
        }
        throw new IllegalStateException("Surface test rule type");
    }

    private static Class<?> runtimeNot() {
        for (Class<?> type : SurfaceRules.class.getDeclaredClasses()) {
            var components = type.getRecordComponents();
            if (components != null && components.length == 1 && components[0].getType() == RUNTIME_CONDITION) {
                return type;
            }
        }
        throw new IllegalStateException("Surface not condition type");
    }

    private record RandomConfig(long low, long high, int type) {
    }

    private record Node(int op, int a, int b, int c, int d, int yes, int no) {
    }

    private record Memo(Object source, Object runtime, int fail) {
        @Override
        public int hashCode() {
            return (System.identityHashCode(this.source) * 31 + System.identityHashCode(this.runtime)) * 31 + this.fail;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Memo memo && this.source == memo.source && this.runtime == memo.runtime && this.fail == memo.fail;
        }
    }

    private static final class Builder {
        private final WorldGenerationContext generation;
        private final Registry<Biome> registry;
        private final Object context;
        private final NativeSurfaceData data = new NativeSurfaceData();
        private boolean steep;
        private boolean contextReusable = true;
        private final List<RandomConfig> randoms = new ArrayList<>();
        private final Map<RandomConfig, Integer> randomIds = new HashMap<>();
        private final List<Node> nodes = new ArrayList<>();
        private final Map<Node, Integer> intern = new HashMap<>();
        private final Map<Memo, Integer> memo = new HashMap<>();
        private final IdentityHashMap<BlockState, Integer> stateIds = new IdentityHashMap<>();
        private final List<BlockState> palette = new ArrayList<>();
        private final IdentityHashMap<ResourceKey<Biome>, Integer> biomeIds = new IdentityHashMap<>();
        private final boolean holderHooks = SurfacePipelineCompatibility.hasForeignHooks(Holder.class)
            || SurfaceRuleHooks.hasHolderHooks(Holder.Reference.class)
            || SurfaceRuleHooks.hasHolderHooks(DIRECT_HOLDER);

        private Builder(WorldGenerationContext generation, Registry<Biome> registry, Object context) {
            this.context = context;
            this.generation = generation;
            this.registry = registry;
            this.contextReusable = !this.holderHooks;
        }

        private int node(Node node) {
            Integer previous = this.intern.get(node);
            if (previous != null) {
                return previous;
            }
            if (this.nodes.size() >= MAX_NODES) {
                throw new IllegalArgumentException("Surface program size");
            }
            int index = this.nodes.size();
            this.nodes.add(node);
            this.intern.put(node, index);
            return index;
        }

        private int result(int value) {
            return this.node(new Node(0, value, 0, 0, 0, 0, 0));
        }

        private int state(BlockState state) {
            Integer previous = this.stateIds.get(state);
            if (previous != null) {
                return previous;
            }
            int next = this.palette.size();
            this.stateIds.put(state, next);
            this.palette.add(state);
            return next;
        }

        private int biomeUse(int target) {
            return this.node(new Node(7, 0, 0, 0, 0, target, 0));
        }

        private int rule(Object source, Object runtime, ResourceKey<Biome> biome, boolean unresolved, int fail, int depth) throws ReflectiveOperationException {
            if (depth > 128) {
                throw new IllegalArgumentException("Surface rule depth");
            }
            Memo key = new Memo(source, runtime, fail);
            Integer previous = this.memo.get(key);
            if (previous != null) {
                return previous;
            }
            Class<?> type = source.getClass();
            int result;
            if (SurfacePipelineCompatibility.hasForeignHooks(type) || SurfaceRuleHooks.hasRuleHooks(runtime.getClass())) {
                this.contextReusable = false;
                result = this.result(FALLBACK);
            } else if (type == BLOCK && runtime.getClass() == RUNTIME_BLOCK) {
                result = this.result(this.state((BlockState) component(runtime, 0)));
            } else if (type == SEQUENCE) {
                List<?> children = (List<?>) component(source, 0);
                if (children.size() == 1) {
                    result = this.rule(children.get(0), runtime, biome, unresolved, fail, depth + 1);
                } else if (runtime.getClass() == RUNTIME_SEQUENCE && component(runtime, 0) instanceof List<?> applied && applied.size() == children.size()) {
                    result = fail;
                    for (int i = children.size() - 1; i >= 0; i--) {
                        result = this.rule(children.get(i), applied.get(i), biome, unresolved, result, depth + 1);
                    }
                } else {
                    result = this.result(FALLBACK);
                }
            } else if (source == SurfaceRules.bandlands() && runtime.getClass().getName().startsWith(type.getName() + "$$Lambda$")) {
                SurfaceSystem system = (SurfaceSystem) captured(runtime, SurfaceSystem.class);
                String signature = "adrenaline$getBand(III)" + org.objectweb.asm.Type.getDescriptor(BlockState.class);
                if (system == null || SurfaceRuleHooks.hooked(system.getClass(), signature)) {
                    this.contextReusable = false;
                    result = this.result(FALLBACK);
                } else {
                    var access = (AdrenalineMixinSurfaceSystemAccess) system;
                    int noise = this.data.noise(access.adrenaline$getClayBandsOffsetNoise(), 0.0D, 0.0D, 0.0D, 1.0D);
                    var palette = new ArrayList<Integer>();
                    for (BlockState state : access.adrenaline$getClayBands()) palette.add(this.state(state));
                    result = noise < 0 ? this.result(FALLBACK)
                        : this.node(new Node(12, noise, this.data.table(palette), palette.size(), this.data.noiseSlot(noise), 0, 0));
                }
            } else if (type == TEST && runtime.getClass() == RUNTIME_TEST) {
                int child = this.rule(component(source, 1), component(runtime, 1), biome, unresolved, fail, depth + 1);
                result = this.condition(component(source, 0), component(runtime, 0), biome, unresolved, child, fail, depth + 1);
            } else if (type.getName().equals("terrablender.worldgen.surface.NamespacedSurfaceRuleSource")
                && runtime.getClass().getName().equals("terrablender.worldgen.surface.NamespacedSurfaceRuleSource$NamespacedRule") && !this.holderHooks) {
                if (unresolved) {
                    result = this.result(BIOME);
                } else {
                    int base = this.rule(component(source, 0), component(runtime, 1), biome, false, fail, depth + 1);
                    Map<?, ?> sources = (Map<?, ?>) component(source, 1);
                    Map<?, ?> applied = (Map<?, ?>) component(runtime, 2);
                    String namespace = biome == null ? null : biome.location().getNamespace();
                    Object selected = namespace == null ? null : sources.get(namespace);
                    result = this.biomeUse(selected == null ? base
                        : this.rule(selected, applied.get(namespace), biome, false, base, depth + 1));
                }
            } else {
                Field delegate = SurfaceRuleDelegates.source(type);
                Field grid = delegate == null && this.stableGrid(source, 0) ? SurfaceRuleDelegates.grid(type, runtime, RUNTIME_RULE) : null;
                if (delegate != null) {
                    result = this.rule((SurfaceRules.RuleSource) delegate.get(source), runtime, biome, unresolved, fail, depth + 1);
                } else if (grid != null && grid.get(runtime) instanceof Object[][] cells && cells.length == 16) {
                    var table = new ArrayList<Integer>(256);
                    for (Object[] row : cells) {
                        if (row.length != 16) throw new IllegalArgumentException("Surface dispatch dimensions");
                        for (Object applied : row) table.add(this.runtimeRule(applied, biome, unresolved, fail, depth + 1));
                    }
                    result = this.node(new Node(14, this.data.table(table), 0, 0, 0, 0, 0));
                } else {
                    result = this.result(FALLBACK);
                }
            }
            this.memo.put(key, result);
            return result;
        }

        private boolean staticSource(Object source, int depth) throws ReflectiveOperationException {
            if (depth > 128 || source == null || SurfacePipelineCompatibility.hasForeignHooks(source.getClass())) return false;
            Class<?> type = source.getClass();
            if (type == BLOCK || source == SurfaceRules.bandlands()) return true;
            if (type == SEQUENCE) {
                for (Object child : (List<?>) component(source, 0)) if (!this.staticSource(child, depth + 1)) return false;
                return true;
            }
            if (type == TEST) {
                Object condition = component(source, 0);
                return this.staticCondition(condition, depth + 1) && this.staticSource(component(source, 1), depth + 1);
            }
            Field delegate = SurfaceRuleDelegates.source(type);
            return delegate != null && this.staticSource(delegate.get(source), depth + 1);
        }

        private boolean staticCondition(Object source, int depth) throws ReflectiveOperationException {
            if (depth > 128 || source == null || source.getClass().getEnclosingClass() != SurfaceRules.class
                || SurfacePipelineCompatibility.hasForeignHooks(source.getClass())) return false;
            return source.getClass() != NOT || this.staticCondition(component(source, 0), depth + 1);
        }

        private boolean stableGrid(Object source, int depth) throws ReflectiveOperationException {
            if (!(this.context instanceof net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi api)) return false;
            var components = source.getClass().getRecordComponents();
            if (components == null || components.length != 1 || components[0].getType() != net.minecraft.util.random.SimpleWeightedRandomList.class) return false;
            var provider = (net.minecraft.util.random.SimpleWeightedRandomList<?>) component(source, 0);
            if (provider.getClass() != net.minecraft.util.random.SimpleWeightedRandomList.class
                || SurfaceRuleHooks.hasWeightedSelectionHooks(provider.getClass())) return false;
            Object factory = ((AdrenalineMixinSurfaceSystemAccess) api.adrenaline$getSystem()).adrenaline$getNoiseRandom();
            if (factory.getClass() != XOROSHIRO_FACTORY && factory.getClass() != LEGACY_FACTORY
                || SurfacePipelineCompatibility.hasForeignHooks(factory.getClass())
                || SurfacePipelineCompatibility.hasForeignHooks(PositionalRandomFactory.class)
                || SurfacePipelineCompatibility.hasForeignHooks(XoroshiroRandomSource.class)
                || SurfacePipelineCompatibility.hasForeignHooks(Xoroshiro128PlusPlus.class)
                || SurfacePipelineCompatibility.hasForeignHooks(LegacyRandomSource.class)
                || SurfacePipelineCompatibility.hasForeignHooks(BitRandomSource.class)) return false;
            List<?> entries = null;
            for (Field field : net.minecraft.util.random.WeightedRandomList.class.getDeclaredFields()) {
                if (field.getType() == com.google.common.collect.ImmutableList.class) {
                    if (entries != null) return false;
                    field.setAccessible(true);
                    entries = (List<?>) field.get(provider);
                }
            }
            if (entries == null) return false;
            for (Object value : entries) {
                if (!(value instanceof net.minecraft.util.random.WeightedEntry.Wrapper<?> entry)) return false;
                if (entry.getClass() != net.minecraft.util.random.WeightedEntry.Wrapper.class
                    || SurfacePipelineCompatibility.hasForeignHooks(entry.getClass()) || !this.staticSource(entry.getData(), depth + 1)) return false;
            }
            return true;
        }

        private int runtimeRule(Object runtime, ResourceKey<Biome> biome, boolean unresolved, int fail, int depth) throws ReflectiveOperationException {
            if (depth > 128) throw new IllegalArgumentException("Surface runtime rule depth");
            Memo key = new Memo(runtime, runtime, fail);
            Integer cached = this.memo.get(key);
            if (cached != null) return cached;
            Class<?> type = runtime.getClass();
            int result;
            if (SurfaceRuleHooks.hasRuleHooks(type)) {
                this.contextReusable = false;
                result = this.result(FALLBACK);
            } else if (type == RUNTIME_BLOCK) {
                result = this.result(this.state((BlockState) component(runtime, 0)));
            } else if (type == RUNTIME_SEQUENCE) {
                List<?> rules = (List<?>) component(runtime, 0);
                result = fail;
                for (int i = rules.size() - 1; i >= 0; i--) result = this.runtimeRule(rules.get(i), biome, unresolved, result, depth + 1);
            } else if (type == RUNTIME_TEST) {
                Object condition = component(runtime, 0);
                result = this.runtimeCondition(condition, biome, unresolved,
                    this.runtimeRule(component(runtime, 1), biome, unresolved, fail, depth + 1), fail, depth + 1);
            } else if (type.getName().startsWith(SurfaceRules.bandlands().getClass().getName() + "$$Lambda$")) {
                result = this.rule(SurfaceRules.bandlands(), runtime, biome, unresolved, fail, depth + 1);
            } else {
                result = this.result(FALLBACK);
            }
            this.memo.put(key, result);
            return result;
        }

        private int runtimeCondition(Object runtime, ResourceKey<Biome> biome, boolean unresolved, int yes, int no, int depth) throws ReflectiveOperationException {
            if (depth > 128) throw new IllegalArgumentException("Surface runtime condition depth");
            if (runtime.getClass() == RUNTIME_NOT && !SurfaceRuleHooks.hasRuleHooks(runtime.getClass())) {
                return this.runtimeCondition(component(runtime, 0), biome, unresolved, no, yes, depth + 1);
            }
            SurfaceRules.ConditionSource source = this.conditionSource(runtime);
            return source == null ? this.result(FALLBACK) : this.condition(source, runtime, biome, unresolved, yes, no, depth + 1);
        }

        private SurfaceRules.ConditionSource conditionSource(Object runtime) throws ReflectiveOperationException {
            Object source = captured(runtime, SurfaceRules.ConditionSource.class);
            if (source instanceof SurfaceRules.ConditionSource condition) return condition;
            Object context = captured(runtime, SurfaceRulesContextFactory.contextType());
            if (context != null) {
                for (SurfaceRules.ConditionSource condition : List.of(SurfaceRules.hole(), SurfaceRules.steep(), SurfaceRules.temperature(), SurfaceRules.abovePreliminarySurface())) {
                    if (!SurfacePipelineCompatibility.hasForeignHooks(condition.getClass()) && SurfaceRulesContextFactory.applyCondition(condition, context) == runtime) return condition;
                }
            }
            return null;
        }

        private int condition(Object source, Object runtime, ResourceKey<Biome> biome, boolean unresolved, int yes, int no, int depth) throws ReflectiveOperationException {
            if (depth > 128) {
                throw new IllegalArgumentException("Surface condition depth");
            }
            Class<?> type = source.getClass();
            if (SurfacePipelineCompatibility.hasForeignHooks(type) || SurfaceRuleHooks.hasRuleHooks(runtime.getClass())) {
                this.contextReusable = false;
                return this.result(FALLBACK);
            }
            if (type == NOT && runtime.getClass() == RUNTIME_NOT) {
                return this.condition(component(source, 0), component(runtime, 0), biome, unresolved, no, yes, depth + 1);
            }
            if (type == BIOME_SOURCE && runtime.getClass().getEnclosingClass() == type && !this.holderHooks) {
                if (unresolved) {
                    return this.result(BIOME);
                }
                var predicate = ((AdrenalineMixinSurfaceBiomeConditionSource) source).adrenaline$biomeNameTest();
                if (!predicate.getClass().getName().startsWith(type.getName() + "$$Lambda$")) {
                    this.contextReusable = false;
                    return this.result(FALLBACK);
                }
                boolean matches = biome != null && predicate.test(biome);
                return this.biomeUse(matches ? yes : no);
            }
            if (type == STONE && runtime.getClass().getEnclosingClass() == type) {
                if ((int) component(source, 2) != 0 && SurfaceRuleHooks.hooked("adrenaline$getSurfaceSecondary()D")) {
                    return this.result(FALLBACK);
                }
                return this.node(new Node(1, (int) component(source, 0), (boolean) component(source, 1) ? 1 : 0,
                    (int) component(source, 2), component(source, 3) == CaveSurface.CEILING ? 1 : 0, yes, no));
            }
            if (type == Y && runtime.getClass().getEnclosingClass() == type) {
                return this.node(new Node(2, ((VerticalAnchor) component(source, 0)).resolveY(this.generation),
                    (int) component(source, 1), (boolean) component(source, 2) ? 1 : 0, 0, yes, no));
            }
            if (type == WATER && runtime.getClass().getEnclosingClass() == type) {
                return this.node(new Node(3, (int) component(source, 0), (int) component(source, 1),
                    (boolean) component(source, 2) ? 1 : 0, 0, yes, no));
            }
            if (source == SurfaceRules.hole() && runtime.getClass().getEnclosingClass() == SurfaceRulesContextFactory.contextType()) {
                return this.node(new Node(4, 0, 0, 0, 0, yes, no));
            }
            if (source == SurfaceRules.abovePreliminarySurface() && !SurfaceRuleHooks.hooked("adrenaline$getMinSurfaceLevel()I") && runtime.getClass().getEnclosingClass() == SurfaceRulesContextFactory.contextType()) {
                return this.node(new Node(5, 0, 0, 0, 0, yes, no));
            }
            if (type == NOISE && runtime.getClass().getEnclosingClass() == type) {
                NormalNoise noise = (NormalNoise) captured(runtime, NormalNoise.class);
                int id = noise == null ? -1 : this.data.noise(noise, (double) component(source, 1), (double) component(source, 2), 0.0D, 1.0D);
                return id < 0 ? this.result(FALLBACK) : this.node(new Node(10, id, this.data.noiseSlot(id), 0, 0, yes, no));
            }
            if (source == SurfaceRules.steep() && runtime.getClass().getEnclosingClass() == SurfaceRulesContextFactory.contextType()) {
                this.steep = true;
                return this.node(new Node(13, 0, 0, 0, 0, yes, no));
            }
            if (source == SurfaceRules.temperature() && runtime.getClass().getEnclosingClass() == SurfaceRulesContextFactory.contextType()) {
                if (!this.data.climates(this.registry)) {
                    this.contextReusable = false;
                    return this.result(FALLBACK);
                }
                return this.node(new Node(11, 0, 0, 0, 0, yes, no));
            }
            if (type == GRADIENT && runtime.getClass().getEnclosingClass() == type) {
                int random = this.random(runtime);
                return this.node(new Node(random < 0 ? 6 : 9, ((VerticalAnchor) component(source, 1)).resolveY(this.generation),
                    ((VerticalAnchor) component(source, 2)).resolveY(this.generation), Math.max(0, random), 0, yes, no));
            }
            return this.result(FALLBACK);
        }
        private int random(Object condition) throws ReflectiveOperationException {
            Object factory = null;
            for (Field field : condition.getClass().getDeclaredFields()) {
                if (PositionalRandomFactory.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    factory = field.get(condition);
                    break;
                }
            }
            if (factory == null || SurfacePipelineCompatibility.hasForeignHooks(factory.getClass())
                || SurfacePipelineCompatibility.hasForeignHooks(PositionalRandomFactory.class)) {
                return -1;
            }
            int type = factory.getClass() == XOROSHIRO_FACTORY ? 2 : factory.getClass() == LEGACY_FACTORY ? 1 : 0;
            if (type == 0 || type == 2 && (SurfacePipelineCompatibility.hasForeignHooks(XoroshiroRandomSource.class)
                || SurfacePipelineCompatibility.hasForeignHooks(Xoroshiro128PlusPlus.class))
                || type == 1 && (SurfacePipelineCompatibility.hasForeignHooks(LegacyRandomSource.class)
                    || SurfacePipelineCompatibility.hasForeignHooks(BitRandomSource.class))) {
                return -1;
            }
            RandomConfig config;
            if (type == 2 && factory instanceof AdrenalineMixinXoroshiroPositionalFactoryAccess access) {
                config = new RandomConfig(access.adrenaline$getSeedLo(), access.adrenaline$getSeedHi(), type);
            } else if (type == 1 && factory instanceof AdrenalineMixinLegacyPositionalFactoryAccess access) {
                config = new RandomConfig(access.adrenaline$getSeed(), 0L, type);
            } else {
                return -1;
            }
            Integer previous = this.randomIds.get(config);
            if (previous != null) {
                return previous;
            }
            if (this.randoms.size() >= 1024) {
                return -1;
            }
            int index = this.randoms.size();
            this.randomIds.put(config, index);
            this.randoms.add(config);
            return index;
        }

    }
}
