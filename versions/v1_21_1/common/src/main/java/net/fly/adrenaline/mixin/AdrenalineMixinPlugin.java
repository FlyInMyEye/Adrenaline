package net.fly.adrenaline.mixin;

import java.util.List;
import java.util.Set;
import net.fly.adrenaline.worldgen.SurfaceRuleHooks;
import net.fly.adrenaline.compat.LightLoadCompat;
import net.fly.adrenaline.compat.MixinPlatform;
import net.fly.adrenaline.compat.NoiseChunkBytecodePatches;
import net.fly.adrenaline.compat.OptimizationTakeoverDetector;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AdrenalineMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        if (MixinPlatform.isModLoaded("lightload")) {
            LightLoadCompat.disable();
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        NoiseChunkBytecodePatches.apply(targetClass, mixinClassName);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        SurfaceRuleHooks.capture(targetClass);
        OptimizationTakeoverDetector.captureClaims(targetClass, mixinInfo);
        OptimizationTakeoverDetector.detectTakeovers(targetClass, MixinPlatform::mixinOwnerName);
    }
}
