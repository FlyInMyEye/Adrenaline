package net.fly.adrenaline.client;

import net.fly.adrenaline.BuildConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class BuildWarningOverlay {

    private static final int TEXT_COLOR = 0xFFFF5555;

    @SubscribeEvent
    public void onRenderTitle(ScreenEvent.Render.Post event) {
        if (!BuildConfig.DEBUG || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        render(event.getGuiGraphics(), event.getScreen().width, Minecraft.getInstance().font, 6);
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!BuildConfig.DEBUG) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.screen != null) {
            return;
        }
        render(event.getGuiGraphics(), minecraft.getWindow().getGuiScaledWidth(), minecraft.font, 4);
    }

    private static void render(GuiGraphics graphics, int width, net.minecraft.client.gui.Font font, int y) {
        String message = "you are running dev build, consider download prod version";
        int textWidth = font.width(message);
        int x = (width - textWidth) / 2;
        graphics.drawString(font, message, x, y, TEXT_COLOR, true);
    }
}
