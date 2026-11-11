package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.WorldLoadStatusWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class MixinTitleScreenBranding extends Screen {

    protected MixinTitleScreenBranding(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void adrenaline$addWorldLoadStatus(CallbackInfo ci) {
        this.addRenderableWidget(new WorldLoadStatusWidget(this.font, 2, this.height - 20));
    }
}
