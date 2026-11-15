package net.fly.adrenaline.mixin.levelgen;

import java.util.function.Predicate;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.SurfaceBiomePredicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$BiomeConditionSource$1BiomeCondition", priority = SurfacePipelineCompatibility.MIXIN_PRIORITY)
public abstract class AdrenalineMixinSurfaceBiomeCondition {

    @Unique private Predicate<ResourceKey<Biome>> adrenaline$originalPredicate;
    @Unique private Predicate<ResourceKey<Biome>> adrenaline$cachedPredicate;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$initializeBiomePredicate(@Coerce Object source, @Coerce Object context, CallbackInfo ci) {
        if (AdrenalineConfig.surfaceOptimizationsEnabled() && !SurfacePipelineCompatibility.hasForeignHooks(source.getClass())) {
            this.adrenaline$originalPredicate = ((AdrenalineMixinSurfaceBiomeConditionSource) source).adrenaline$biomeNameTest();
            this.adrenaline$cachedPredicate = new SurfaceBiomePredicate(this.adrenaline$originalPredicate);
        }
    }

    @ModifyArg(method = "compute", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/Holder;is(Ljava/util/function/Predicate;)Z"), index = 0)
    private Predicate<ResourceKey<Biome>> adrenaline$cacheBiomeMembership(Predicate<ResourceKey<Biome>> predicate) {
        return predicate == this.adrenaline$originalPredicate ? this.adrenaline$cachedPredicate : predicate;
    }
}
