package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.AdrenalineFluidStatusAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Aquifer.FluidStatus.class)
public class MixinAquiferFluidStatus implements AdrenalineFluidStatusAccess {

    @Shadow @Final private int fluidLevel;
    @Shadow @Final private BlockState fluidType;

    @Override
    public int adrenaline$fluidLevel() {
        return this.fluidLevel;
    }

    @Override
    public BlockState adrenaline$fluidType() {
        return this.fluidType;
    }
}
