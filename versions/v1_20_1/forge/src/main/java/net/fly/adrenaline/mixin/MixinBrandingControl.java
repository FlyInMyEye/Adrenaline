package net.fly.adrenaline.mixin;

import java.util.List;
import java.util.function.BiConsumer;
import net.fly.adrenaline.client.BackgroundWorldSave;
import net.fly.adrenaline.client.BackgroundWorldgenWarmup;
import net.minecraftforge.internal.BrandingControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BrandingControl.class, remap = false)
public class MixinBrandingControl {

    @Shadow
    private static List<String> brandings;

    @Shadow
    private static List<String> brandingsNoMC;

    @Inject(method = "forEachLine", at = @At("TAIL"), remap = false)
    private static void adrenaline$addWarmupBranding(boolean includeMinecraft, boolean reverse, BiConsumer<Integer, String> lineConsumer, CallbackInfo ci) {
        if (BackgroundWorldgenWarmup.isRunning()) {
            List<String> lines = includeMinecraft ? brandings : brandingsNoMC;
            lineConsumer.accept(lines.size(), BackgroundWorldgenWarmup.branding());
        } else if (BackgroundWorldSave.isRunning()) {
            List<String> lines = includeMinecraft ? brandings : brandingsNoMC;
            lineConsumer.accept(lines.size(), "Adrenaline saving world...");
        }
    }
}
