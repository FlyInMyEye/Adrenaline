package net.fly.adrenaline.mixin;

import java.util.List;
import java.util.Set;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compat.LightLoadCompat;
import net.fly.adrenaline.compat.MixinPlatform;
import net.fly.adrenaline.compat.NoiseChunkBytecodePatches;
import net.fly.adrenaline.compat.OptimizationTakeoverDetector;
import net.fly.adrenaline.compatdata.ModernFixCompat;
import net.fly.adrenaline.worldgen.SurfaceRuleHooks;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AdrenalineMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> DEBUG_MIXINS = Set.of(
        MixinPlatform.DEBUG_MIXIN,
        "net.fly.adrenaline.mixin.MixinLoggerChunkProgressListener",
        "net.fly.adrenaline.mixin.MixinMinecraftServerWorldgenDebug",
        "net.fly.adrenaline.mixin.MixinPlayerList",
        "net.fly.adrenaline.mixin.MixinServerChunkCacheDebug",
        "net.fly.adrenaline.mixin.MixinWorldgenDifferenceInput"
    );

    @Override
    public void onLoad(String mixinPackage) {
        if (MixinPlatform.isModLoaded("modernfix")) {
            ModernFixCompat.configureEarly();
        }
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
        if (mixinClassName.equals("net.fly.adrenaline.mixin.MixinTectonicInvert") && !MixinPlatform.isModLoaded("tectonic")) {
            return false;
        }
        return BuildConfig.DEBUG || !DEBUG_MIXINS.contains(mixinClassName);
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
