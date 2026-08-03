package net.fly.adrenaline.mixin.levelgen;

import com.google.common.base.Suppliers;
import java.util.function.Function;
import java.util.function.Supplier;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public abstract class AdrenalineMixinSurfaceRulesContext {

    @Shadow @Final private Function<BlockPos, Holder<Biome>> biomeGetter;
    @Shadow @Final private BlockPos.MutableBlockPos pos;
    @Shadow long lastUpdateY;
    @Shadow Supplier<Holder<Biome>> biome;
    @Shadow int blockY;
    @Shadow int waterHeight;
    @Shadow int stoneDepthBelow;
    @Shadow int stoneDepthAbove;

    @Unique private final Supplier<Holder<Biome>> adrenaline$biomeSupplier = this::adrenaline$getBiome;
    @Unique private Holder<Biome> adrenaline$cachedBiome;
    @Unique private int adrenaline$biomeX;
    @Unique private int adrenaline$biomeY;
    @Unique private int adrenaline$biomeZ;
    @Unique private boolean adrenaline$biomeDirty = true;

    /**
     * @author Fly
     * @reason Remove per-step biome supplier allocation
     */
    @Overwrite
    public void updateY(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int blockX, int blockY, int blockZ) {
        if (!AdrenalineConfig.surfaceOptimizationsEnabled()) {
            this.lastUpdateY++;
            this.biome = Suppliers.memoize(() -> this.biomeGetter.apply(this.pos.set(blockX, blockY, blockZ)));
            this.blockY = blockY;
            this.waterHeight = waterHeight;
            this.stoneDepthBelow = stoneDepthBelow;
            this.stoneDepthAbove = stoneDepthAbove;
            return;
        }

        this.lastUpdateY++;
        this.adrenaline$biomeX = blockX;
        this.adrenaline$biomeY = blockY;
        this.adrenaline$biomeZ = blockZ;
        this.adrenaline$biomeDirty = true;
        this.biome = this.adrenaline$biomeSupplier;
        this.blockY = blockY;
        this.waterHeight = waterHeight;
        this.stoneDepthBelow = stoneDepthBelow;
        this.stoneDepthAbove = stoneDepthAbove;
    }

    @Unique
    private Holder<Biome> adrenaline$getBiome() {
        if (this.adrenaline$biomeDirty) {
            this.adrenaline$cachedBiome = this.biomeGetter.apply(this.pos.set(this.adrenaline$biomeX, this.adrenaline$biomeY, this.adrenaline$biomeZ));
            this.adrenaline$biomeDirty = false;
        }

        return this.adrenaline$cachedBiome;
    }
}
