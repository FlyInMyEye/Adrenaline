package net.fly.adrenaline.mixin;

import java.util.List;
import java.util.Set;
import net.fly.adrenaline.compatdata.IncompatibilityRegistry;
import net.fly.adrenaline.compatdata.IncompatibleData;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public class AdrenalineMixinPlugin implements IMixinConfigPlugin {

    private static Set<String> disabledMixins = Set.of();

    @Override
    public void onLoad(String mixinPackage) {
        if (LoadingModList.get() == null) {
            disabledMixins = Set.of();
            return;
        }

        Set<String> mixins = new java.util.HashSet<>();
        for (IncompatibleData incompatibility : IncompatibilityRegistry.all()) {
            if (!IncompatibilityRegistry.isLoaded(incompatibility.modId())) {
                continue;
            }

            for (String mixin : incompatibility.incompatibleMixins()) {
                mixins.add(mixin);
            }
        }
        disabledMixins = Set.copyOf(mixins);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !disabledMixins.contains(mixinClassName);
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
