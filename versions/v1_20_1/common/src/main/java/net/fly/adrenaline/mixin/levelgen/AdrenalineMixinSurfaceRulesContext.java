package net.fly.adrenaline.mixin.levelgen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.function.Function;
import com.google.common.base.Supplier;
import net.fly.adrenaline.worldgen.SurfacePointEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public abstract class AdrenalineMixinSurfaceRulesContext {

    @Shadow @Final private Function<BlockPos, Holder<Biome>> biomeGetter;
    @Shadow @Final private BlockPos.MutableBlockPos pos;
    @Unique private final Supplier<Holder<Biome>> adrenaline$biomeSupplier = this::adrenaline$getBiome;
    @Unique private Holder<Biome> adrenaline$cachedBiome;
    @Unique private int adrenaline$biomeX;
    @Unique private int adrenaline$biomeY;
    @Unique private int adrenaline$biomeZ;
    @Unique private boolean adrenaline$biomeDirty = true;

    @WrapOperation(method = "updateY", at = @At(value = "INVOKE", target = "Lcom/google/common/base/Suppliers;memoize(Lcom/google/common/base/Supplier;)Lcom/google/common/base/Supplier;"), require = 0)
    private Supplier<Holder<Biome>> adrenaline$reuseBiomeSupplier(Supplier<Holder<Biome>> supplier, Operation<Supplier<Holder<Biome>>> original,
                                                                @Local(argsOnly = true, ordinal = 3) int x,
                                                                @Local(argsOnly = true, ordinal = 4) int y,
                                                                @Local(argsOnly = true, ordinal = 5) int z) {
        if (!SurfacePointEvaluator.canCacheBiome(this)) {
            return original.call(supplier);
        }
        this.adrenaline$biomeX = x;
        this.adrenaline$biomeY = y;
        this.adrenaline$biomeZ = z;
        this.adrenaline$biomeDirty = true;
        this.adrenaline$cachedBiome = null;
        return this.adrenaline$biomeSupplier;
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
