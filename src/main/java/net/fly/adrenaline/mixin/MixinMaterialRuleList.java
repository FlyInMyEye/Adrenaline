package net.fly.adrenaline.mixin;

import java.util.List;

import net.fly.adrenaline.worldgen.AdrenalineMaterialRuleListAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.material.MaterialRuleList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(MaterialRuleList.class)
public class MixinMaterialRuleList implements AdrenalineMaterialRuleListAccess {

    @Shadow @Final private List<NoiseChunk.BlockStateFiller> materialRuleList;

    @Override
    public int adrenaline$ruleCount() {
        return this.materialRuleList.size();
    }
}
