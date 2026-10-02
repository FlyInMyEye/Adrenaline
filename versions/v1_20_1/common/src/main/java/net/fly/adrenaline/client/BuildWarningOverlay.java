package net.fly.adrenaline.client;

import net.fly.adrenaline.BuildConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class BuildWarningOverlay {

    private static final int TEXT_COLOR = 0xFFFF5555;

    private BuildWarningOverlay() {
    }

    public static void renderTitle(GuiGraphics graphics, int width) {
        if (BuildConfig.DEBUG) {
            render(graphics, width, 6);
        }
    }

    public static void renderGui(GuiGraphics graphics) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.screen == null) {
            render(graphics, minecraft.getWindow().getGuiScaledWidth(), 4);
        }
    }

    private static void render(GuiGraphics graphics, int width, int y) {
        var font = Minecraft.getInstance().font;
        String message = Component.translatable("gui.adrenaline.dev_build_warning").getString();
        int x = (width - font.width(message)) / 2;
        graphics.drawString(font, message, x, y, TEXT_COLOR, true);
    }
}
