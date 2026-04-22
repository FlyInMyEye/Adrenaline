package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.AdrenalineConfigScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class MixinOptionsScreen extends Screen {

    protected MixinOptionsScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void adrenaline$addConfigShortcut(CallbackInfo ci) {
        this.addRenderableWidget(Button.builder(Component.literal("A").setStyle(Style.EMPTY.withColor(0xFF59BD)), button -> this.minecraft.setScreen(new AdrenalineConfigScreen(this))).bounds(4, this.height - 24, 20, 20).build());
    }
}
