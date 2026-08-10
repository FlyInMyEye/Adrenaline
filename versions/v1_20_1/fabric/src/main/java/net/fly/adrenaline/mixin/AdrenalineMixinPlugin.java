package net.fly.adrenaline.mixin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compat.OptimizationTakeoverDetector;
import net.fly.adrenaline.compatdata.IncompatibilityRegistry;
import net.fly.adrenaline.compatdata.IncompatibleData;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AdrenalineMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> DEBUG_MIXINS = Set.of(
        "net.fly.adrenaline.mixin.MixinGui",
        "net.fly.adrenaline.mixin.MixinLoggerChunkProgressListener",
        "net.fly.adrenaline.mixin.MixinMinecraftServerWorldgenDebug",
        "net.fly.adrenaline.mixin.MixinPlayerList",
        "net.fly.adrenaline.mixin.MixinWorldgenDifferenceInput"
    );

    private Set<String> disabledMixins = Set.of();

    @Override
    public void onLoad(String mixinPackage) {
        Set<String> mixins = new HashSet<>();
        for (IncompatibleData incompatibility : IncompatibilityRegistry.all()) {
            if (IncompatibilityRegistry.isLoaded(incompatibility.modId())) {
                mixins.addAll(List.of(incompatibility.incompatibleMixins()));
            }
        }
        this.disabledMixins = Set.copyOf(mixins);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return (BuildConfig.DEBUG || !DEBUG_MIXINS.contains(mixinClassName)) && !this.disabledMixins.contains(mixinClassName);
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
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.contains("OptimizationTakeoverDetector")) {
            OptimizationTakeoverDetector.captureClaims(targetClass, mixinInfo);
            OptimizationTakeoverDetector.detectTakeovers(targetClass, AdrenalineMixinPlugin::displayName);
        } else {
            OptimizationTakeoverDetector.captureClaims(targetClass, mixinInfo);
        }
    }

    private static String displayName(String mixinClassName) {
        String resource = mixinClassName.replace('.', '/') + ".class";
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (container.findPath(resource).isPresent()) {
                return container.getMetadata().getName();
            }
        }
        return mixinClassName;
    }
}
