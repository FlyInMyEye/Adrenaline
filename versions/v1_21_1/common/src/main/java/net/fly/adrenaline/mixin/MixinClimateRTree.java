package net.fly.adrenaline.mixin;

import net.fly.adrenaline.worldgen.ClimateColumnIndex;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.world.level.biome.Climate$RTree")
public abstract class MixinClimateRTree implements ClimateColumnIndex.Access {

    @Shadow @Final private ThreadLocal<Object> lastResult;

    @Unique private volatile ClimateColumnIndex adrenaline$columnIndex;
    @Unique private volatile boolean adrenaline$columnIndexInitialized;

    @Override
    public ClimateColumnIndex adrenaline$columnIndex() {
        if (!this.adrenaline$columnIndexInitialized) {
            this.adrenaline$initializeIndex();
        }
        return this.adrenaline$columnIndex;
    }

    @Unique
    private synchronized void adrenaline$initializeIndex() {
        if (!this.adrenaline$columnIndexInitialized) {
            this.adrenaline$columnIndex = ClimateColumnIndex.createTree(this, this.lastResult);
            this.adrenaline$columnIndexInitialized = true;
        }
    }
}
