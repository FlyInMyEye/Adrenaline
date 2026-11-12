package net.fly.adrenaline.mixin;

import net.fly.adrenaline.client.AdrenalineConfigButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class MixinCreateWorldScreen extends Screen {

    @Unique
    private Button adrenaline$configButton;

    protected MixinCreateWorldScreen(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;repositionElements()V"))
    private void adrenaline$addConfigButton(CallbackInfo ci) {
        this.adrenaline$configButton = this.addRenderableWidget(new AdrenalineConfigButton(this, 0, 0));
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void adrenaline$positionConfigButton(CallbackInfo ci) {
        if (this.adrenaline$configButton != null) {
            FrameLayout.alignInRectangle(this.adrenaline$configButton, 4, this.height - 36, 20, 36, 0.0F, 0.5F);
        }
    }

}
