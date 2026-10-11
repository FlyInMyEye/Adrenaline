package net.fly.adrenaline.worldgen;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.RecordComponent;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
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
            return new SurfaceRulePlan(rule(source, context, generation, 0), contextApi);
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

    private static Rule rule(SurfaceRules.RuleSource source, Object context, WorldGenerationContext generation, int depth) throws ReflectiveOperationException {
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
                rules[i] = rule((SurfaceRules.RuleSource) sequence.get(i), context, generation, depth + 1);
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
            Condition condition = condition((SurfaceRules.ConditionSource) component(source, 0), context, generation, depth + 1);
            Rule child = rule((SurfaceRules.RuleSource) component(source, 1), context, generation, depth + 1);
            return plan -> condition.test(plan) && !plan.uncertain ? child.apply(plan) : null;
        }
        if (namespaced(source)) {
            Rule base = rule((SurfaceRules.RuleSource) component(source, 0), context, generation, depth + 1);
            Map<?, ?> sources = (Map<?, ?>) component(source, 1);
            Map<String, Rule> rules = new HashMap<>();
            for (var entry : sources.entrySet()) {
                rules.put((String) entry.getKey(), rule((SurfaceRules.RuleSource) entry.getValue(), context, generation, depth + 1));
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
            return rule(repeatingRule(source), context, generation, depth + 1);
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

    private static Condition condition(SurfaceRules.ConditionSource source, Object context, WorldGenerationContext generation, int depth) throws ReflectiveOperationException {
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
            Condition target = condition((SurfaceRules.ConditionSource) component(source, 0), context, generation, depth + 1);
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
            return builtInTree(source, 0);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    private static boolean builtInTree(Object node, int depth) throws ReflectiveOperationException {
        if (depth > 128) {
            return false;
        }
        if (namespaced(node)) {
            if (!builtInTree(component(node, 0), depth + 1)) {
                return false;
            }
            for (Object child : ((Map<?, ?>) component(node, 1)).values()) {
                if (!builtInTree(child, depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        if (repeatingNoise(node)) {
            return builtInTree(repeatingRule(node), depth + 1);
        }
        if (bands(node) || spatialNoise(node)) {
            return true;
        }
        if (weighted(node)) {
            var rules = (SimpleWeightedRandomList<?>) component(node, 0);
            for (var entry : rules.unwrap()) {
                if (!builtInTree(entry.getData(), depth + 1)) {
                    return false;
                }
            }
            return true;
        }
        if (node.getClass().getEnclosingClass() != SurfaceRules.class) {
            return false;
        }
        RecordComponent[] components = node.getClass().getRecordComponents();
        if (components == null) {
            return true;
        }
        for (int i = 0; i < components.length; i++) {
            Object value = component(node, i);
            if (value instanceof SurfaceRules.RuleSource || value instanceof SurfaceRules.ConditionSource) {
                if (!builtInTree(value, depth + 1)) {
                    return false;
                }
            } else if (value instanceof List<?> children) {
                for (Object child : children) {
                    if ((child instanceof SurfaceRules.RuleSource || child instanceof SurfaceRules.ConditionSource)
                        && !builtInTree(child, depth + 1)) {
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
        RecordComponent component = record.getClass().getRecordComponents()[index];
        var accessor = component.getAccessor();
        accessor.setAccessible(true);
        return accessor.invoke(record);
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
