package net.fly.adrenaline.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class AdrenalineConfigButton extends Button {
    public AdrenalineConfigButton(Screen parent, int x, int y) {
        super(x, y, 20, 20, Component.literal("A").withStyle(style -> style.withColor(0xFF59BD)),
            button -> Minecraft.getInstance().setScreen(AdrenalineConfigScreen.create(parent)), DEFAULT_NARRATION);
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("gui.adrenaline.config.title");
    }
}
