package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;
import net.fly.adrenaline.config.AdrenalineConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = {
    "net.minecraft.world.level.levelgen.RandomState$1NoiseWiringHelper",
    "net.minecraft.world.level.levelgen.RandomState$1"
})
public class MixinRandomStateVisitor {

    @Unique
    private Map<Object, Object> adrenaline$identityCache;

    @WrapOperation(method = "apply", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object adrenaline$avoidRecursiveDensityHashing(Map<Object, Object> wrapped, Object function, Function<Object, Object> mapper, Operation<Object> original) {
        if (!AdrenalineConfig.noiseChunkOptimizationsEnabled()) {
            return original.call(wrapped, function, mapper);
        }
        if (this.adrenaline$identityCache == null) {
            this.adrenaline$identityCache = new IdentityHashMap<>();
        }
        return this.adrenaline$identityCache.computeIfAbsent(function, mapper);
    }
}
