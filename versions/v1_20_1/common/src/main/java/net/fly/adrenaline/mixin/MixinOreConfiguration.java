package net.fly.adrenaline.mixin;

import java.util.List;
import net.fly.adrenaline.worldgen.OreTargetLookup;
import net.fly.adrenaline.worldgen.OreTargets;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration.TargetBlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(OreConfiguration.class)
public class MixinOreConfiguration implements OreTargetLookup {
    @Shadow @Final public List<TargetBlockState> targetStates;
    @Unique private volatile OreTargets.Lookup adrenaline$targets;

    @Override
    public OreTargets.Lookup adrenaline$oreTargets() {
        OreTargets.Lookup lookup = this.adrenaline$targets;
        if (lookup == null || !lookup.current(this.targetStates)) lookup = this.adrenaline$compileTargets();
        return lookup;
    }

    @Unique
    private synchronized OreTargets.Lookup adrenaline$compileTargets() {
        OreTargets.Lookup lookup = this.adrenaline$targets;
        if (lookup == null || !lookup.current(this.targetStates)) {
            this.adrenaline$targets = lookup = new OreTargets.Lookup(this.targetStates);
        }
        return lookup;
    }
}
