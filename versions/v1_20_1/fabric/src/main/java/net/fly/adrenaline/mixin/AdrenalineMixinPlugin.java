package net.fly.adrenaline.mixin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fly.adrenaline.compatdata.IncompatibilityRegistry;
import net.fly.adrenaline.compatdata.IncompatibleData;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AdrenalineMixinPlugin implements IMixinConfigPlugin {
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
        return !this.disabledMixins.contains(mixinClassName);
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
    }
}
