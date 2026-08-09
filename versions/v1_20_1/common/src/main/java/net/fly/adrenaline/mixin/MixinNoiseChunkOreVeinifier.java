package net.fly.adrenaline.mixin;

import java.util.List;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinCacheAllInCellAccessor;
import net.fly.adrenaline.worldgen.AdrenalineMaterialRuleListAccess;
import net.fly.adrenaline.worldgen.AdrenalineNoiseChunkMaterialAccess;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunk.class)
public class MixinNoiseChunkOreVeinifier implements AdrenalineNoiseChunkMaterialAccess {

    private static final BlockState COPPER_ORE = Blocks.COPPER_ORE.defaultBlockState();
    private static final BlockState RAW_COPPER_BLOCK = Blocks.RAW_COPPER_BLOCK.defaultBlockState();
    private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
    private static final BlockState DEEPSLATE_IRON_ORE = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
    private static final BlockState RAW_IRON_BLOCK = Blocks.RAW_IRON_BLOCK.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();

    @Shadow @Final private List<?> cellCaches;
    @Shadow @Final private NoiseChunk.BlockStateFiller blockStateRule;

    @Unique
    private NoiseChunk.BlockStateFiller adrenaline$oreVeinFiller;

    @Unique
    private double[] adrenaline$finalDensityValues;

    @Unique
    private boolean adrenaline$hasDirectMaterialPath;

    @Unique private DensityFunction adrenaline$veinToggle;
    @Unique private DensityFunction adrenaline$veinRidged;
    @Unique private DensityFunction adrenaline$veinGap;
    @Unique private PositionalRandomFactory adrenaline$oreRandomFactory;
    @Unique private double[] adrenaline$veinToggleValues;
    @Unique private double[] adrenaline$veinRidgedValues;
    @Unique private double[] adrenaline$veinGapValues;

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/OreVeinifier;create(Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;)Lnet/minecraft/world/level/levelgen/NoiseChunk$BlockStateFiller;"
        )
    )
    @ControlsOptimization(Optimization.ORE_VEIN)
    private NoiseChunk.BlockStateFiller adrenaline$fastOreVeinifier(DensityFunction veinToggle, DensityFunction veinRidged, DensityFunction veinGap, PositionalRandomFactory randomFactory) {
        NoiseChunk.BlockStateFiller filler;
        if (!AdrenalineConfig.oreVeinOptimizationsEnabled()) {
            filler = MixinOreVeinifierInvoker.adrenaline$create(veinToggle, veinRidged, veinGap, randomFactory);
        } else {
            this.adrenaline$veinToggle = veinToggle;
            this.adrenaline$veinRidged = veinRidged;
            this.adrenaline$veinGap = veinGap;
            this.adrenaline$oreRandomFactory = randomFactory;
            filler = context -> {
                double veininess = veinToggle.compute(context);
                int blockY = context.blockY();
                boolean copper = veininess > 0.0D;
                int maxY = copper ? 50 : -8;
                int minY = copper ? 0 : -60;
                int maxDelta = maxY - blockY;
                int minDelta = blockY - minY;
                if (minDelta < 0 || maxDelta < 0) {
                    return null;
                }

                double absVeininess = Math.abs(veininess);
                int edge = Math.min(maxDelta, minDelta);
                double edgeRoundoff = Mth.clampedMap(edge, 0.0D, 20.0D, -0.2D, 0.0D);
                if (absVeininess + edgeRoundoff < 0.4000000059604645D) {
                    return null;
                }

                RandomSource random = randomFactory.at(context.blockX(), blockY, context.blockZ());
                if (random.nextFloat() > 0.7F) {
                    return null;
                }
                if (veinRidged.compute(context) >= 0.0D) {
                    return null;
                }

                double richness = Mth.clampedMap(absVeininess, 0.4000000059604645D, 0.6000000238418579D, 0.10000000149011612D, 0.30000001192092896D);
                if (random.nextFloat() < richness && veinGap.compute(context) > -0.30000001192092896D) {
                    if (copper) {
                        return random.nextFloat() < 0.02F ? RAW_COPPER_BLOCK : COPPER_ORE;
                    }
                    return random.nextFloat() < 0.02F ? RAW_IRON_BLOCK : DEEPSLATE_IRON_ORE;
                }

                return copper ? GRANITE : TUFF;
            };
        }
        this.adrenaline$oreVeinFiller = filler;
        return filler;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    @ControlsOptimization(Optimization.MATERIAL_RULE)
    private void adrenaline$captureMaterialPath(CallbackInfo ci) {
        if (!AdrenalineConfig.materialRuleOptimizationsEnabled() || this.cellCaches.isEmpty()
            || !(this.blockStateRule instanceof AdrenalineMaterialRuleListAccess rules)) {
            return;
        }

        int expectedRules = this.adrenaline$oreVeinFiller == null ? 1 : 2;
        if (rules.adrenaline$ruleCount() != expectedRules) {
            return;
        }

        Object finalDensityCache = this.cellCaches.get(this.cellCaches.size() - 1);
        if (finalDensityCache instanceof AdrenalineMixinCacheAllInCellAccessor cache) {
            this.adrenaline$finalDensityValues = cache.adrenaline$getValues();
            if (this.adrenaline$veinToggle != null) {
                int size = this.adrenaline$finalDensityValues.length;
                this.adrenaline$veinToggleValues = new double[size];
                this.adrenaline$veinRidgedValues = new double[size];
                this.adrenaline$veinGapValues = new double[size];
            }
            this.adrenaline$hasDirectMaterialPath = true;
        }
    }

    @Override
    public boolean adrenaline$hasDirectMaterialPath() {
        return this.adrenaline$hasDirectMaterialPath;
    }

    @Override
    public double[] adrenaline$finalDensityValues() {
        return this.adrenaline$finalDensityValues;
    }

    @Override
    public boolean adrenaline$hasPrecomputedMaterialPath() {
        return this.adrenaline$hasDirectMaterialPath && (this.adrenaline$oreVeinFiller == null || this.adrenaline$veinToggleValues != null);
    }

    @Override
    public void adrenaline$fillMaterialArrays(DensityFunction.ContextProvider contextProvider) {
        if (this.adrenaline$veinToggleValues == null) {
            return;
        }
        this.adrenaline$veinToggle.fillArray(this.adrenaline$veinToggleValues, contextProvider);
        this.adrenaline$veinRidged.fillArray(this.adrenaline$veinRidgedValues, contextProvider);
        this.adrenaline$veinGap.fillArray(this.adrenaline$veinGapValues, contextProvider);
    }

    @Override
    public BlockState adrenaline$calculateOre(DensityFunction.FunctionContext context, int index) {
        if (this.adrenaline$oreVeinFiller == null) {
            return null;
        }
        if (this.adrenaline$veinToggleValues == null) {
            return this.adrenaline$oreVeinFiller.calculate(context);
        }

        double veininess = this.adrenaline$veinToggleValues[index];
        int blockY = context.blockY();
        boolean copper = veininess > 0.0D;
        int maxY = copper ? 50 : -8;
        int minY = copper ? 0 : -60;
        int maxDelta = maxY - blockY;
        int minDelta = blockY - minY;
        if (minDelta < 0 || maxDelta < 0) {
            return null;
        }
        double absVeininess = Math.abs(veininess);
        int edge = Math.min(maxDelta, minDelta);
        double edgeRoundoff = Mth.clampedMap(edge, 0.0D, 20.0D, -0.2D, 0.0D);
        if (absVeininess + edgeRoundoff < 0.4000000059604645D) {
            return null;
        }
        RandomSource random = this.adrenaline$oreRandomFactory.at(context.blockX(), blockY, context.blockZ());
        if (random.nextFloat() > 0.7F || this.adrenaline$veinRidgedValues[index] >= 0.0D) {
            return null;
        }
        double richness = Mth.clampedMap(absVeininess, 0.4000000059604645D, 0.6000000238418579D, 0.10000000149011612D, 0.30000001192092896D);
        if (random.nextFloat() < richness && this.adrenaline$veinGapValues[index] > -0.30000001192092896D) {
            if (copper) {
                return random.nextFloat() < 0.02F ? RAW_COPPER_BLOCK : COPPER_ORE;
            }
            return random.nextFloat() < 0.02F ? RAW_IRON_BLOCK : DEEPSLATE_IRON_ORE;
        }
        return copper ? GRANITE : TUFF;
    }
}
