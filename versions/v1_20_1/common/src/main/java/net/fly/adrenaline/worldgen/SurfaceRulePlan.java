package net.fly.adrenaline.worldgen;

import java.lang.reflect.RecordComponent;
import java.util.List;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
    private final Rule root;
    private final AdrenalineMixinSurfaceRulesContextApi context;
    private int y;
    private int above;
    private int below;
    private int water;
    private int bottom;
    private boolean uncertain;

    private SurfaceRulePlan(Rule root, AdrenalineMixinSurfaceRulesContextApi context) {
        this.root = root;
        this.context = context;
    }

    public static SurfaceRulePlan compile(SurfaceRules.RuleSource source, Object context, WorldGenerationContext generation) {
        try {
            return new SurfaceRulePlan(rule(source, generation, 0), (AdrenalineMixinSurfaceRulesContextApi) context);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    public BlockState evaluate(int x, int y, int z, int above, int below, int water, int runBottom) {
        this.y = y;
        this.above = above;
        this.below = below;
        this.water = water;
        this.bottom = runBottom;
        this.uncertain = false;
        return this.root.apply(this);
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

    private static Rule rule(SurfaceRules.RuleSource source, WorldGenerationContext generation, int depth) throws ReflectiveOperationException {
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
                rules[i] = rule((SurfaceRules.RuleSource) sequence.get(i), generation, depth + 1);
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
            Condition condition = condition((SurfaceRules.ConditionSource) component(source, 0), generation, depth + 1);
            Rule child = rule((SurfaceRules.RuleSource) component(source, 1), generation, depth + 1);
            return plan -> condition.test(plan) && !plan.uncertain ? child.apply(plan) : null;
        }
        return plan -> {
            plan.uncertain = true;
            return null;
        };
    }

    private static Condition condition(SurfaceRules.ConditionSource source, WorldGenerationContext generation, int depth) throws ReflectiveOperationException {
        if (depth > 128) {
            throw new IllegalArgumentException("Surface condition depth");
        }
        if (source == SurfaceRules.abovePreliminarySurface()) {
            return plan -> plan.atLeast(plan.context.adrenaline$getMinSurfaceLevel());
        }
        if (source == SurfaceRules.hole()) {
            return plan -> plan.context.adrenaline$getSurfaceDepth() <= 0;
        }
        if (source.getClass() == NOT) {
            Condition target = condition((SurfaceRules.ConditionSource) component(source, 0), generation, depth + 1);
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
            return plan -> {
                if (plan.y <= low) {
                    return true;
                }
                if (plan.y >= high) {
                    plan.bottom = Math.max(plan.bottom, Math.max(high, low + 1));
                    return false;
                }
                plan.uncertain = true;
                return false;
            };
        }
        return plan -> {
            plan.uncertain = true;
            return false;
        };
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
