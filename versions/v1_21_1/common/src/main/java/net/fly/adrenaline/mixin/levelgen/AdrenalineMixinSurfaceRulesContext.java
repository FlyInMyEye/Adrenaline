package net.fly.adrenaline.mixin.levelgen;

import java.util.function.Function;
import java.util.function.Supplier;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.worldgen.SurfacePointEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public abstract class AdrenalineMixinSurfaceRulesContext {

    @Shadow @Final private Function<BlockPos, Holder<Biome>> biomeGetter;
    @Shadow @Final private BlockPos.MutableBlockPos pos;
    @Shadow private long lastUpdateY;
    @Shadow private Supplier<Holder<Biome>> biome;
    @Shadow private int blockY;
    @Shadow private int waterHeight;
    @Shadow private int stoneDepthBelow;
    @Shadow private int stoneDepthAbove;
    @Unique private final Supplier<Holder<Biome>> adrenaline$biomeSupplier = this::adrenaline$getBiome;
    @Unique private Holder<Biome> adrenaline$cachedBiome;
    @Unique private int adrenaline$biomeX;
    @Unique private int adrenaline$biomeY;
    @Unique private int adrenaline$biomeZ;
    @Unique private boolean adrenaline$biomeDirty = true;

    @Inject(method = "updateY", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.SURFACE_BIOMES)
    private void adrenaline$reuseBiomeSupplier(int stoneDepthAbove, int stoneDepthBelow, int waterHeight, int x, int y, int z, CallbackInfo ci) {
        if (!SurfacePointEvaluator.canCacheBiome(this)) {
            return;
        }
        this.adrenaline$biomeX = x;
        this.adrenaline$biomeY = y;
        this.adrenaline$biomeZ = z;
        this.adrenaline$biomeDirty = true;
        this.adrenaline$cachedBiome = null;
        this.lastUpdateY++;
        this.biome = this.adrenaline$biomeSupplier;
        this.blockY = y;
        this.waterHeight = waterHeight;
        this.stoneDepthBelow = stoneDepthBelow;
        this.stoneDepthAbove = stoneDepthAbove;
        ci.cancel();
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
