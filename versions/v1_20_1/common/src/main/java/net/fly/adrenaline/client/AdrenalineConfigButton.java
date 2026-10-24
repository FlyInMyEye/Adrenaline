package net.fly.adrenaline.client;

import net.fly.adrenaline.compat.ModBlacklist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (ModBlacklist.shouldWarn()) {
            graphics.drawString(Minecraft.getInstance().font, "!", this.getX() + this.width - 5, this.getY() + 1, 0xFFFF5555);
        }
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        MutableComponent message = Component.translatable("gui.adrenaline.config.title");
        if (ModBlacklist.shouldWarn()) {
            message.append(". ").append(Component.translatable("gui.adrenaline.blacklist.title"));
        }
        return message;
    }
}
