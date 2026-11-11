package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.BuildWarningOverlay;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreen extends Screen {

    protected MixinTitleScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void adrenaline$addBuildWarning(CallbackInfo ci) {
        MultiLineTextWidget warning = BuildWarningOverlay.createTitleWidget(this.font, this.width);
        if (warning != null) {
            this.addRenderableWidget(warning);
        }
    }
}
