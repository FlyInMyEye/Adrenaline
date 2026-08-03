package net.fly.adrenaline.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FastloadCompatScreen extends Screen {

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("textures/gui/options_background.png");

    public FastloadCompatScreen() {
        super(Component.translatable("gui.adrenaline.compat.title"));
    }

    @Override
    protected void init() {
        int buttonWidth = 160;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.compat.quit_game"), button -> {
            if (this.minecraft != null) {
                this.minecraft.stop();
            }
        }).bounds(this.width / 2 - buttonWidth / 2, this.height - 52, buttonWidth, 20).build());
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
            new FormattedLine(Component.translatable("gui.adrenaline.compat.fastload.line1"), 0xFFE0E0E0),
            new FormattedLine(Component.translatable("gui.adrenaline.compat.fastload.line2"), 0xFFE0E0E0),
            new FormattedLine(Component.translatable("gui.adrenaline.compat.fastload.line3"), 0xFFFFD060)
        );
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
