package net.fly.adrenaline.client;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compat.ModBlacklist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class BuildWarningOverlay {

    private static final int TEXT_COLOR = 0xFFFF5555;

    private BuildWarningOverlay() {
    }

    public static void renderTitle(GuiGraphics graphics, int width) {
        render(graphics, width, 6);
    }

    public static void renderGui(GuiGraphics graphics) {
        if (!BuildConfig.DEBUG && !ModBlacklist.shouldWarn()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.screen == null) {
            render(graphics, minecraft.getWindow().getGuiScaledWidth(), 4);
        }
    }

    private static void render(GuiGraphics graphics, int width, int y) {
        Component message = message();
        if (message == null) {
            return;
        }
        var font = Minecraft.getInstance().font;
        for (FormattedCharSequence line : font.split(message, Math.max(1, width - 12))) {
            int x = (width - font.width(line)) / 2;
            graphics.drawString(font, line, x, y, TEXT_COLOR, true);
            y += font.lineHeight;
        }
    }

    static Component message() {
        if (BuildConfig.DEBUG) {
            return Component.translatable("gui.adrenaline.dev_build_warning");
        }
        return ModBlacklist.shouldWarn() ? Component.translatable("gui.adrenaline.blacklist.warning") : null;
    }
}
