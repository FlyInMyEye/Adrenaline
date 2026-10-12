package net.fly.adrenaline.worldgen;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Noises;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.placement.CaveSurface;

public final class SurfaceRulePlan {

    private static final Class<?> BLOCK = SurfaceRules.state(Blocks.STONE.defaultBlockState()).getClass();
    private static final Class<?> SEQUENCE = SurfaceRules.sequence(SurfaceRules.state(Blocks.STONE.defaultBlockState())).getClass();
    private static final Class<?> TEST = SurfaceRules.ifTrue(SurfaceRules.ON_FLOOR, SurfaceRules.state(Blocks.STONE.defaultBlockState())).getClass();
    private static final Class<?> NOT = SurfaceRules.not(SurfaceRules.ON_FLOOR).getClass();
    private static final Class<?> Y = SurfaceRules.yBlockCheck(VerticalAnchor.absolute(0), 0).getClass();
    private static final Class<?> WATER = SurfaceRules.waterBlockCheck(0, 0).getClass();
    private static final Class<?> STONE = SurfaceRules.ON_FLOOR.getClass();
    private static final Class<?> GRADIENT = SurfaceRules.verticalGradient("adrenaline:plan", VerticalAnchor.absolute(0), VerticalAnchor.absolute(1)).getClass();
    private static final Class<?> NOISE = SurfaceRules.noiseCondition(Noises.SURFACE, 0.0D).getClass();
    private static final Class<?> BIOME = SurfaceRules.isBiome(Biomes.PLAINS).getClass();
    private static final ClassValue<MethodHandle[]> RECORD_ACCESSORS = new ClassValue<>() {
        @Override
        protected MethodHandle[] computeValue(Class<?> type) {
            RecordComponent[] components = type.getRecordComponents();
            if (components == null) {
                throw new IllegalArgumentException("Surface rule is not a record");
            }
            MethodHandle[] accessors = new MethodHandle[components.length];
            try {
                for (int i = 0; i < components.length; i++) {
                    var accessor = components[i].getAccessor();
                    accessor.setAccessible(true);
                    accessors[i] = MethodHandles.lookup().unreflect(accessor)
                        .asType(MethodType.methodType(Object.class, Object.class));
                }
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Could not access surface rule", exception);
            }
            return accessors;
        }
    };
    private final Rule root;
    private final AdrenalineMixinSurfaceRulesContextApi context;
    private int x;
    private int y;
    private int z;
    private int above;
    private int below;
    private int water;
    private int bottom;
    private boolean uncertain;
    private boolean biomeCertified;
    private SurfaceBiomeCache biomes;

    private SurfaceRulePlan(Rule root, AdrenalineMixinSurfaceRulesContextApi context) {
        this.root = root;
        this.context = context;
    }

    public static SurfaceRulePlan compile(SurfaceRules.RuleSource source, Object context, WorldGenerationContext generation, AdrenalineMixinSurfaceRulesContextApi contextApi) {
        try {
            return new SurfaceRulePlan(new Builder(context, generation).rule(source, 0), contextApi);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    public BlockState evaluate(int x, int y, int z, int above, int below, int water, int runBottom) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.above = above;
        this.below = below;
        this.water = water;
        this.bottom = runBottom;
        this.uncertain = false;
        this.biomeCertified = false;
        return this.root.apply(this);
    }

    public void biomes(SurfaceBiomeCache biomes) {
        this.biomes = biomes;
    }

    public boolean uncertain() {
        return this.uncertain;
    }

    public int bottom() {
        return this.bottom;
    }

    private boolean atLeast(int threshold) {
        boolean result = this.y >= threshold;
        if (result) {
            this.bottom = Math.max(this.bottom, threshold);
        }
        return result;
    }

    private boolean atMost(int threshold) {
        boolean result = this.y <= threshold;
        if (!result) {
            this.bottom = Math.max(this.bottom, threshold + 1);
        }
        return result;
    }

    private static Rule rule(SurfaceRules.RuleSource source, Object context, WorldGenerationContext generation, int depth, Builder builder) throws ReflectiveOperationException {
        if (depth > 128) {
            throw new IllegalArgumentException("Surface rule depth");
        }
        if (source.getClass() == BLOCK) {
            BlockState state = (BlockState) component(source, 0);
            return plan -> state;
        }
        if (source.getClass() == SEQUENCE) {
            List<?> sequence = (List<?>) component(source, 0);
            Rule[] rules = new Rule[sequence.size()];
            for (int i = 0; i < rules.length; i++) {
                rules[i] = builder.rule((SurfaceRules.RuleSource) sequence.get(i), depth + 1);
            }
            return plan -> {
                for (Rule child : rules) {
                    BlockState result = child.apply(plan);
                    if (result != null || plan.uncertain) {
                        return result;
                    }
                }
                return null;
            };
        }
        if (source.getClass() == TEST) {
            Condition condition = builder.condition((SurfaceRules.ConditionSource) component(source, 0), depth + 1);
            Rule child = builder.rule((SurfaceRules.RuleSource) component(source, 1), depth + 1);
            return plan -> condition.test(plan) && !plan.uncertain ? child.apply(plan) : null;
        }
        if (namespaced(source)) {
            Rule base = builder.rule((SurfaceRules.RuleSource) component(source, 0), depth + 1);
            Map<?, ?> sources = (Map<?, ?>) component(source, 1);
            Map<String, Rule> rules = new HashMap<>();
            for (var entry : sources.entrySet()) {
                rules.put((String) entry.getKey(), builder.rule((SurfaceRules.RuleSource) entry.getValue(), depth + 1));
            }
            Map<Holder<Biome>, Rule> selections = new IdentityHashMap<>();
            return plan -> {
                plan.certifyBiome();
                Holder<Biome> biome = plan.context.adrenaline$getBiome().get();
                Rule selected = selections.get(biome);
                if (selected == null) {
                    var key = biome.unwrapKey();
                    selected = key.isPresent() ? rules.getOrDefault(key.get().location().getNamespace(), base) : base;
                    if (key.isPresent()) {
                        selections.put(biome, selected);
                    }
                }
                BlockState result = selected.apply(plan);
                return result != null || plan.uncertain || selected == base ? result : base.apply(plan);
            };
        }
        if (repeatingNoise(source)) {
            return builder.rule(repeatingRule(source), depth + 1);
        }
        if (bands(source) || weighted(source)) {
            MethodHandle apply = SurfaceRulesContextFactory.createTryApplyHandle(SurfaceRulesContextFactory.apply(source, context));
            return plan -> {
                plan.bottom = plan.y;
                try {
                    return (BlockState) apply.invokeExact(plan.x, plan.y, plan.z);
                } catch (Throwable throwable) {
                    throw new IllegalStateException("Surface rule evaluation failed", throwable);
                }
            };
        }
        return plan -> {
            plan.uncertain = true;
            return null;
        };
    }

    private static Condition condition(SurfaceRules.ConditionSource source, Object context, WorldGenerationContext generation, int depth, Builder builder) throws ReflectiveOperationException {
        if (depth > 128) {
            throw new IllegalArgumentException("Surface condition depth");
        }
        if (source.getClass() == NOISE || source == SurfaceRules.steep()) {
            BooleanSupplier test = SurfaceRulesContextFactory.createCondition(source, context);
            return plan -> test.getAsBoolean();
        }
        if (source.getClass() == BIOME) {
            BooleanSupplier test = SurfaceRulesContextFactory.createCondition(source, context);
            return plan -> {
                plan.certifyBiome();
                return test.getAsBoolean();
            };
        }
        if (source == SurfaceRules.abovePreliminarySurface()) {
            return plan -> plan.atLeast(plan.context.adrenaline$getMinSurfaceLevel());
        }
        if (source == SurfaceRules.hole()) {
            return plan -> plan.context.adrenaline$getSurfaceDepth() <= 0;
        }
        if (source.getClass() == NOT) {
            Condition target = builder.condition((SurfaceRules.ConditionSource) component(source, 0), depth + 1);
            return plan -> !target.test(plan);
        }
        if (source.getClass() == Y) {
            int anchor = ((VerticalAnchor) component(source, 0)).resolveY(generation);
            int multiplier = (int) component(source, 1);
            boolean addStone = (boolean) component(source, 2);
            return plan -> {
                int threshold = anchor + plan.context.adrenaline$getSurfaceDepth() * multiplier;
                return addStone ? plan.y + plan.above >= threshold : plan.atLeast(threshold);
            };
        }
        if (source.getClass() == WATER) {
            int offset = (int) component(source, 0);
            int multiplier = (int) component(source, 1);
            boolean addStone = (boolean) component(source, 2);
            return plan -> {
                if (plan.water == Integer.MIN_VALUE) {
                    return true;
                }
                int threshold = plan.water + offset + plan.context.adrenaline$getSurfaceDepth() * multiplier;
                return addStone ? plan.y + plan.above >= threshold : plan.atLeast(threshold);
            };
        }
        if (source.getClass() == STONE) {
            int offset = (int) component(source, 0);
            boolean addSurface = (boolean) component(source, 1);
            int secondaryRange = (int) component(source, 2);
            boolean ceiling = component(source, 3) == CaveSurface.CEILING;
            return plan -> {
                int secondary = secondaryRange == 0 ? 0 : (int) Mth.map(plan.context.adrenaline$getSurfaceSecondary(), -1.0, 1.0, 0.0, secondaryRange);
                int limit = 1 + offset + (addSurface ? plan.context.adrenaline$getSurfaceDepth() : 0) + secondary;
                return ceiling ? plan.atMost(plan.y - plan.below + limit) : plan.atLeast(plan.y + plan.above - limit);
            };
        }
        if (source.getClass() == GRADIENT) {
            int low = ((VerticalAnchor) component(source, 1)).resolveY(generation);
            int high = ((VerticalAnchor) component(source, 2)).resolveY(generation);
            BooleanSupplier test = SurfaceRulesContextFactory.createCondition(source, context);
            return plan -> {
                if (plan.y <= low) {
                    return true;
                }
                if (plan.y >= high) {
                    plan.bottom = Math.max(plan.bottom, Math.max(high, low + 1));
                    return false;
                }
                plan.bottom = plan.y;
                return test.getAsBoolean();
            };
        }
        if (source.getClass().getEnclosingClass() == SurfaceRules.class || spatialNoise(source)) {
            BooleanSupplier test = SurfaceRulesContextFactory.createCondition(source, context);
            return plan -> {
                plan.bottom = plan.y;
                return test.getAsBoolean();
            };
        }
        return plan -> {
            plan.uncertain = true;
            return false;
        };
    }

    public static boolean supportsBufferedWrites(SurfaceRules.RuleSource source) {
        try {
            return builtInTree(source, 0, new IdentityHashMap<>());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean builtInTree(Object node, int depth, Map<Object, Boolean> known) throws ReflectiveOperationException {
        if (depth > 128) {
            return false;
        }
        Boolean previous = known.get(node);
        if (previous != null) {
            return previous;
        }
        known.put(node, false);
        boolean result = inspectBuiltInTree(node, depth, known);
        known.put(node, result);
        return result;
    }

    private static boolean inspectBuiltInTree(Object node, int depth, Map<Object, Boolean> known) throws ReflectiveOperationException {
        if (SurfacePipelineCompatibility.hasForeignHooks(node.getClass())) {
            return false;
        }
        if (namespaced(node)) {
            if (!builtInTree(component(node, 0), depth + 1, known)) {
                return false;
            }
            for (Object child : ((Map<?, ?>) component(node, 1)).values()) {
                if (!builtInTree(child, depth + 1, known)) {
                    return false;
                }
            }
            return true;
        }
        if (repeatingNoise(node)) {
            return builtInTree(repeatingRule(node), depth + 1, known);
        }
        if (bands(node) || spatialNoise(node)) {
            return true;
        }
        if (weighted(node)) {
            var rules = (SimpleWeightedRandomList<?>) component(node, 0);
            for (var entry : rules.unwrap()) {
                if (!builtInTree(entry.getData(), depth + 1, known)) {
                    return false;
                }
            }
            return true;
        }
        if (node.getClass().getEnclosingClass() != SurfaceRules.class) {
            return false;
        }
        if (!node.getClass().isRecord()) {
            return true;
        }
        int components = RECORD_ACCESSORS.get(node.getClass()).length;
        for (int i = 0; i < components; i++) {
            Object value = component(node, i);
            if (value instanceof SurfaceRules.RuleSource || value instanceof SurfaceRules.ConditionSource) {
                if (!builtInTree(value, depth + 1, known)) {
                    return false;
                }
            } else if (value instanceof List<?> children) {
                for (Object child : children) {
                    if ((child instanceof SurfaceRules.RuleSource || child instanceof SurfaceRules.ConditionSource)
                        && !builtInTree(child, depth + 1, known)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void certifyBiome() {
        if (!this.biomeCertified) {
            this.bottom = Math.max(this.bottom, this.biomes == null ? this.y : this.biomes.certifiedBottom(this.y, this.bottom));
            this.biomeCertified = true;
        }
    }

    private static boolean namespaced(Object source) {
        return source.getClass().getName().equals("terrablender.worldgen.surface.NamespacedSurfaceRuleSource");
    }

    private static boolean repeatingNoise(Object source) {
        return source.getClass().getName().equals("net.potionstudios.biomeswevegone.world.level.levelgen.surfacerules.BetweenRepeatingNoiseRange");
    }

    private static SurfaceRules.RuleSource repeatingRule(Object source) throws ReflectiveOperationException {
        var field = source.getClass().getDeclaredField("rule");
        field.setAccessible(true);
        return (SurfaceRules.RuleSource) field.get(source);
    }

    private static boolean bands(Object source) {
        return source.getClass().getName().equals("net.potionstudios.biomeswevegone.world.level.levelgen.surfacerules.BandsRuleSource");
    }

    private static boolean weighted(Object source) {
        return source.getClass().getName().equals("net.potionstudios.biomeswevegone.world.level.levelgen.surfacerules.WeightedRuleSource");
    }

    private static boolean spatialNoise(Object source) {
        return source.getClass().getName().equals("com.blackgear.vanillabackport.common.worldgen.surface.SpatialNoiseThresholdConditionSource");
    }

    private static Object component(Object record, int index) throws ReflectiveOperationException {
        try {
            return (Object) RECORD_ACCESSORS.get(record.getClass())[index].invokeExact(record);
        } catch (Throwable failure) {
            throw new ReflectiveOperationException("Could not read surface rule", failure);
        }
    }

    private static final class Builder {

        private final Object context;
        private final WorldGenerationContext generation;
        private final Map<SurfaceRules.RuleSource, Rule> rules = new IdentityHashMap<>();
        private final Map<SurfaceRules.ConditionSource, Condition> conditions = new IdentityHashMap<>();

        private Builder(Object context, WorldGenerationContext generation) {
            this.context = context;
            this.generation = generation;
        }

        private Rule rule(SurfaceRules.RuleSource source, int depth) throws ReflectiveOperationException {
            Rule known = this.rules.get(source);
            if (known == null) {
                known = SurfaceRulePlan.rule(source, this.context, this.generation, depth, this);
                this.rules.put(source, known);
            }
            return known;
        }

        private Condition condition(SurfaceRules.ConditionSource source, int depth) throws ReflectiveOperationException {
            Condition known = this.conditions.get(source);
            if (known == null) {
                known = SurfaceRulePlan.condition(source, this.context, this.generation, depth, this);
                this.conditions.put(source, known);
            }
            return known;
        }
    }

    @FunctionalInterface
    private interface Rule {
        BlockState apply(SurfaceRulePlan plan);
    }

    @FunctionalInterface
    private interface Condition {
        boolean test(SurfaceRulePlan plan);
    }
}
