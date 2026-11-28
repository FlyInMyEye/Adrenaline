package net.fly.adrenaline.mixin;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compatdata.ModernFixCompat;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import net.fly.adrenaline.worldgen.SurfaceRuleHooks;
import net.fly.adrenaline.compat.LightLoadCompat;
import net.fly.adrenaline.compat.MixinPlatform;
import net.fly.adrenaline.compat.NoiseChunkBytecodePatches;
import net.fly.adrenaline.compat.OptimizationTakeoverDetector;
import net.fly.adrenaline.compat.OreCandidateBytecode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

public final class AdrenalineMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> DEBUG_MIXINS = Set.of(
        MixinPlatform.DEBUG_MIXIN,
        "net.fly.adrenaline.mixin.MixinLoggerChunkProgressListener",
        "net.fly.adrenaline.mixin.MixinMinecraftServerWorldgenDebug",
        "net.fly.adrenaline.mixin.MixinPlayerList",
        "net.fly.adrenaline.mixin.MixinWorldgenDifferenceInput",
        "net.fly.adrenaline.mixin.MixinServerChunkCacheDebug",
        "net.fly.adrenaline.mixin.MixinNoiseBasedChunkGeneratorDebug",
        "net.fly.adrenaline.mixin.MixinChunkGenerationTaskTiming"
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
        if (mixinClassName.equals("net.fly.adrenaline.mixin.compat.MixinZombieAwareness")) {
            return supportsZombieAwarenessData(targetClassName);
        }
        if (mixinClassName.equals("net.fly.adrenaline.mixin.MixinTectonicInvert") && !MixinPlatform.isModLoaded("tectonic")) {
            return false;
        }
        if (mixinClassName.equals("net.fly.adrenaline.mixin.MixinStreamsClimateSampler")
            || mixinClassName.equals("net.fly.adrenaline.mixin.MixinStreamsWorldGenRegion")) {
            return MixinPlatform.isModLoaded("streamsreflowing");
        }
        if (mixinClassName.equals("net.fly.adrenaline.mixin.MixinClimatePositionalSearch")) {
            return supportsExpressionInjectors();
        }
        return BuildConfig.DEBUG || !DEBUG_MIXINS.contains(mixinClassName);
    }

    private static boolean supportsZombieAwarenessData(String targetClassName) {
        if (!MixinPlatform.isModLoaded("zombieawareness")) return false;
        try {
            ClassNode target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName, false);
            return target.fields.stream().anyMatch(field -> field.name.equals("entityData") && field.desc.equals("Ljava/util/HashMap;"))
                && target.methods.stream().anyMatch(method -> method.name.equals("getPersistentData"))
                && target.methods.stream().anyMatch(method -> method.name.equals("setPersistentData"));
        } catch (ClassNotFoundException | IOException ignored) {
            return false;
        }
    }

    private static boolean supportsExpressionInjectors() {
        try {
            MixinService.getService().getClassProvider().findClass("com.llamalad7.mixinextras.expression.Definition", false);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
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
        if (BuildConfig.DEBUG) {
            OreCandidateBytecode.apply(targetClass, mixinClassName);
        }
        SurfaceRuleHooks.capture(targetClass);
        OptimizationTakeoverDetector.captureClaims(targetClass, mixinInfo);
        OptimizationTakeoverDetector.detectTakeovers(targetClass, MixinPlatform::mixinOwnerName);
    }
}
