package net.fly.adrenaline.client;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldgenDifferencesScreen extends Screen {

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("textures/gui/options_background.png");
    private static final int ROW_HEIGHT = 20;

    private final Screen parent;
    private final WorldgenBenchmark.Report report;
    private boolean noiseStage;
    private int scrollOffset;
    private int targetScrollOffset;
    private int maxScroll;
    private double animatedScrollOffset;
    private boolean scrolling;

    public WorldgenDifferencesScreen(Screen parent, WorldgenBenchmark.Report report) {
        super(Component.translatable("gui.adrenaline.benchmark.differences_title", report.noiseDifferences().size()));
        this.parent = parent;
        this.report = report;
        this.noiseStage = !report.noiseDifferences().isEmpty();
    }

    @Override
    protected void init() {
        boolean hasBothStages = !this.report.noiseDifferences().isEmpty() && !this.report.fullDifferences().isEmpty();
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.minecraft.setScreen(this.parent)).bounds(hasBothStages ? this.width / 2 + 4 : this.width / 2 - 50, this.height - 28, 100, 20).build());
        if (hasBothStages) {
            this.addRenderableWidget(Button.builder(this.toggleLabel(), button -> {
                this.noiseStage = !this.noiseStage;
                this.setScrollImmediate(0.0D);
                this.updateMaxScroll();
                button.setMessage(this.toggleLabel());
            }).bounds(this.width / 2 - 104, this.height - 28, 100, 20).build());
        }
        this.updateMaxScroll();
    }

    private void updateMaxScroll() {
        int viewportHeight = Math.max(0, this.height - 72);
        this.maxScroll = Math.max(0, this.differences().size() * ROW_HEIGHT - viewportHeight);
        this.targetScrollOffset = Math.min(this.targetScrollOffset, this.maxScroll);
        this.animatedScrollOffset = Math.min(this.animatedScrollOffset, this.maxScroll);
        this.scrollOffset = Math.min(this.scrollOffset, this.maxScroll);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.updateAnimatedScroll();
        this.renderBackground(guiGraphics);
        this.renderTiledPanel(guiGraphics, 0, 0, this.width, this.height);
        Component title = Component.translatable("gui.adrenaline.benchmark.differences_stage_title", this.noiseStage ? "NOISE" : "FULL", this.differences().size());
        guiGraphics.drawCenteredString(this.font, title, this.width / 2, 14, 16777215);
        int top = 34;
        int bottom = this.height - 38;
        guiGraphics.enableScissor(0, top, this.width, bottom);
        int firstRow = this.scrollOffset / ROW_HEIGHT;
        int y = top - this.scrollOffset % ROW_HEIGHT;
        int visibleRows = (bottom - top) / ROW_HEIGHT + 2;
        List<WorldgenBenchmark.BlockDifference> differences = this.differences();
        int end = Math.min(differences.size(), firstRow + visibleRows);
        for (int index = firstRow; index < end; index++) {
            WorldgenBenchmark.BlockDifference difference = differences.get(index);
            String text = String.format(
                "%d. %d, %d, %d: %s -> %s",
                index + 1,
                difference.x(),
                difference.y(),
                difference.z(),
                WorldgenBenchmark.stateName(difference.vanillaStateId()),
                WorldgenBenchmark.stateName(difference.adrenalineStateId())
            );
            if (index % 2 == 0) {
                guiGraphics.fill(4, y, this.width - 11, y + ROW_HEIGHT, 0x28000000);
            }
            guiGraphics.renderItem(this.stateItem(difference.vanillaStateId()), 8, y + 2);
            guiGraphics.drawString(this.font, ">", 30, y + 6, 0xA0A0A0, false);
            guiGraphics.renderItem(this.stateItem(difference.adrenalineStateId()), 40, y + 2);
            String visible = this.font.plainSubstrByWidth(text, this.width - 74);
            guiGraphics.drawString(this.font, visible, 62, y + 6, index % 2 == 0 ? 0xE0E0E0 : 0xB0B0B0, false);
            y += ROW_HEIGHT;
        }
        guiGraphics.disableScissor();
        this.renderScrollBar(guiGraphics, top, bottom);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        this.targetScrollOffset = Math.max(0, Math.min(this.maxScroll, this.targetScrollOffset - (int) Math.signum(delta) * 16));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.maxScroll > 0 && mouseX >= this.scrollbarX() && mouseX < this.scrollbarX() + 6
            && mouseY >= this.scrollbarTop() && mouseY <= this.scrollbarBottom()) {
            this.scrolling = true;
            this.setDragging(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.scrolling = false;
        this.setDragging(false);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button != 0 || !this.scrolling) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        int top = this.scrollbarTop();
        int bottom = this.scrollbarBottom();
        if (mouseY < top) {
            this.setScrollImmediate(0.0D);
        } else if (mouseY > bottom) {
            this.setScrollImmediate(this.maxScroll);
        } else {
            int trackHeight = bottom - top;
            int thumbHeight = this.scrollbarThumbHeight(trackHeight);
            double scrollRate = Math.max(1.0D, (double) this.maxScroll / (double) (trackHeight - thumbHeight));
            this.setScrollImmediate(this.scrollOffset + dragY * scrollRate);
        }
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private List<WorldgenBenchmark.BlockDifference> differences() {
        return this.noiseStage ? this.report.noiseDifferences() : this.report.fullDifferences();
    }

    private Component toggleLabel() {
        return Component.translatable(this.noiseStage ? "gui.adrenaline.benchmark.show_full" : "gui.adrenaline.benchmark.show_noise");
    }

    private ItemStack stateItem(int stateId) {
        BlockState state = Block.BLOCK_STATE_REGISTRY.byId(stateId);
        return state == null ? ItemStack.EMPTY : new ItemStack(state.getBlock());
    }

    private void updateAnimatedScroll() {
        if (this.scrollOffset == this.targetScrollOffset && this.animatedScrollOffset == this.targetScrollOffset) {
            return;
        }

        this.animatedScrollOffset += (this.targetScrollOffset - this.animatedScrollOffset) * 0.35D;
        if (Math.abs(this.targetScrollOffset - this.animatedScrollOffset) < 0.5D) {
            this.animatedScrollOffset = this.targetScrollOffset;
        }
        this.scrollOffset = (int) Math.round(this.animatedScrollOffset);
    }

    private void renderScrollBar(GuiGraphics guiGraphics, int top, int bottom) {
        if (this.maxScroll <= 0) {
            return;
        }
        int trackHeight = bottom - top;
        int thumbHeight = this.scrollbarThumbHeight(trackHeight);
        int thumbY = top + (trackHeight - thumbHeight) * this.scrollOffset / this.maxScroll;
        int x = this.scrollbarX();
        guiGraphics.fill(x, top, x + 6, bottom, 0xFF000000);
        guiGraphics.fill(x, thumbY, x + 6, thumbY + thumbHeight, 0xFF808080);
        guiGraphics.fill(x, thumbY, x + 5, thumbY + thumbHeight - 1, 0xFFC0C0C0);
    }

    private int scrollbarX() {
        return this.width - 8;
    }

    private int scrollbarTop() {
        return 34;
    }

    private int scrollbarBottom() {
        return this.height - 38;
    }

    private int scrollbarThumbHeight(int trackHeight) {
        int totalHeight = trackHeight + this.maxScroll;
        return Math.max(32, Math.min(trackHeight * trackHeight / totalHeight, trackHeight - 8));
    }

    private void setScrollImmediate(double offset) {
        int value = (int) Math.round(Math.max(0.0D, Math.min(this.maxScroll, offset)));
        this.targetScrollOffset = value;
        this.animatedScrollOffset = value;
        this.scrollOffset = value;
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
}
