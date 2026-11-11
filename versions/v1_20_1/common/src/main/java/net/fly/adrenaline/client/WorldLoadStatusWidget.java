package net.fly.adrenaline.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class WorldLoadStatusWidget extends AbstractWidget {

    private final Font font;

    public WorldLoadStatusWidget(Font font, int x, int y) {
        super(x, y, font.width("Adrenaline warmup -"), font.lineHeight, Component.literal("Adrenaline warmup -"));
        this.font = font;
        this.active = false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        String status = WorldLoadStatus.text();
        if (status == null) {
            return;
        }
        this.setMessage(Component.literal(status));
        this.setWidth(this.font.width(this.getMessage()));
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderScrollingString(graphics, this.font, 0, 0xFFFFFF | Mth.ceil(this.alpha * 255.0F) << 24);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
