package net.fly.adrenaline.client;

import net.fly.adrenaline.compatdata.ModernFixCompat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class ModernFixCompatScreen extends Screen {

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("textures/gui/options_background.png");

    private Component status = Component.empty();

    public ModernFixCompatScreen() {
        super(Component.translatable("gui.adrenaline.compat.title"));
    }

    @Override
    protected void init() {
        int buttonWidth = 160;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.compat.fix_and_restart"), button -> this.fixAndRestart()).bounds(this.width / 2 - buttonWidth / 2, this.height - 52, buttonWidth, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.renderTiledPanel(guiGraphics, 0, 0, this.width, this.height);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 16777215);

        int y = 72;
        for (FormattedLine line : this.bodyLines()) {
            guiGraphics.drawCenteredString(this.font, line.text(), this.width / 2, y, line.color());
            y += 12;
        }

        if (!this.status.getString().isEmpty()) {
            guiGraphics.drawCenteredString(this.font, this.status, this.width / 2, this.height - 78, 0xFF8080);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private List<FormattedLine> bodyLines() {
        return List.of(
            new FormattedLine(Component.translatable("gui.adrenaline.compat.modernfix.line1"), 0xFFE0E0E0),
            new FormattedLine(Component.translatable("gui.adrenaline.compat.modernfix.line2"), 0xFFE0E0E0),
            new FormattedLine(Component.translatable("gui.adrenaline.compat.modernfix.line3"), 0xFFFFD060)
        );
    }

    private void fixAndRestart() {
        if (!ModernFixCompat.applyConfigFix()) {
            this.status = Component.translatable("gui.adrenaline.compat.modernfix.status.failed");
            return;
        }

        this.status = Component.translatable("gui.adrenaline.compat.modernfix.status.updated");
        if (this.minecraft != null) {
            this.minecraft.stop();
        }
    }

    private void renderTiledPanel(GuiGraphics guiGraphics, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0xAA000000);
        for (int drawY = 0; drawY < height; drawY += 32) {
            for (int drawX = 0; drawX < width; drawX += 32) {
                int tileWidth = Math.min(32, width - drawX);
                int tileHeight = Math.min(32, height - drawY);
                guiGraphics.blit(PANEL_TEXTURE, x + drawX, y + drawY, 0, 0, tileWidth, tileHeight, 32, 32);
            }
        }
        guiGraphics.fill(x, y, x + width, y + height, 0x88000000);
    }

    private record FormattedLine(Component text, int color) {
    }
}
