package net.fly.adrenaline.mixin;

import java.util.List;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.AdrenalineMaterialRuleListAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MaterialRuleList.class)
public class MixinMaterialRuleList implements AdrenalineMaterialRuleListAccess {

    @Shadow @Final private List<NoiseChunk.BlockStateFiller> materialRuleList;

    @Override
    public int adrenaline$ruleCount() {
        return this.materialRuleList.size();
    }

    /**
     * @author Fly
     * @reason Inline common filler counts
     */
    @Overwrite
    public BlockState calculate(DensityFunction.FunctionContext context) {
        if (!AdrenalineConfig.materialRuleOptimizationsEnabled()) {
            for (NoiseChunk.BlockStateFiller filler : this.materialRuleList) {
                BlockState state = filler.calculate(context);
                if (state != null) {
                    return state;
                }
            }
            return null;
        }

        int size = this.materialRuleList.size();
        if (size == 0) {
            return null;
        }

        if (size == 1) {
            return this.materialRuleList.get(0).calculate(context);
        }

        if (size == 2) {
            BlockState first = this.materialRuleList.get(0).calculate(context);
            if (first != null) {
                return first;
            }

            return this.materialRuleList.get(1).calculate(context);
        }

        for (int i = 0; i < size; i++) {
            BlockState state = this.materialRuleList.get(i).calculate(context);
            if (state != null) {
                return state;
            }
        }

        return null;
    }
}
