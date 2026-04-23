package net.fly.adrenaline.client;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class AdrenalineConfigScreen extends Screen {

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("textures/gui/options_background.png");

    private final Screen parent;
    private final AdrenalineConfig.Data config;
    private final int availableProcessors;
    private final List<ScrollableWidget> scrollableWidgets = new ArrayList<>();
    private final List<ScrollableLabel> scrollableLabels = new ArrayList<>();

    private WorkerThreadsSlider workerThreadsSlider;
    private SpawnZoneRadiusSlider spawnZoneRadiusSlider;
    private Button worldgenOptimizationsButton;
    private Button terrainFillOptimizationsButton;
    private Button surfaceOptimizationsButton;
    private Button noiseChunkOptimizationsButton;
    private Button materialRuleOptimizationsButton;
    private Button aquiferOptimizationsButton;
    private Button beardifierOptimizationsButton;
    private Button oreVeinOptimizationsButton;
    private Button parallelWorldgenButton;
    private Button fastLegacyRandomButton;
    private Button debugLoggingButton;
    private Button doneButton;
    private int scrollOffset;
    private int targetScrollOffset;
    private int maxScroll;
    private int contentBottom;
    private double animatedScrollOffset;

    public AdrenalineConfigScreen(Screen parent) {
        super(Component.literal("Adrenaline"));
        this.parent = parent;
        this.config = AdrenalineConfig.copy();
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
    }

    @Override
    protected void init() {
        this.scrollableWidgets.clear();
        this.scrollableLabels.clear();
        this.scrollOffset = 0;
        this.targetScrollOffset = 0;
        this.animatedScrollOffset = 0.0D;
        int centerX = this.width / 2;
        int y = 54 + this.warningLines().size() * 10;
        int leftX = centerX - 185;
        int buttonX = centerX + 95;
        int labelX = leftX;
        int buttonWidth = 70;

        y = this.addSectionHeader("General", y);
        this.workerThreadsSlider = this.addScrollableWidget(new WorkerThreadsSlider(leftX, y, 370, 20), y);
        y += 30;
        this.spawnZoneRadiusSlider = this.addScrollableWidget(new SpawnZoneRadiusSlider(leftX, y, 370, 20), y);
        y += 30;
        this.worldgenOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Worldgen optimization"), this.config.worldgenOptimizations, value -> this.config.worldgenOptimizations = value);
        y += 24;
        this.parallelWorldgenButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Parallel worldgen"), this.config.parallelWorldgen, value -> this.config.parallelWorldgen = value);
        y += 24;

        y = this.addSectionHeader("Worldgen optimization", y);
        this.terrainFillOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Terrain fill optimizations"), this.config.terrainFillOptimizations, value -> this.config.terrainFillOptimizations = value);
        y += 24;
        this.surfaceOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Surface optimizations"), this.config.surfaceOptimizations, value -> this.config.surfaceOptimizations = value);
        y += 24;
        this.noiseChunkOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Noise chunk optimizations"), this.config.noiseChunkOptimizations, value -> this.config.noiseChunkOptimizations = value);
        y += 24;
        this.materialRuleOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Material rule optimizations"), this.config.materialRuleOptimizations, value -> this.config.materialRuleOptimizations = value);
        y += 24;
        this.aquiferOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Aquifer optimizations"), this.config.aquiferOptimizations, value -> this.config.aquiferOptimizations = value);
        y += 24;
        this.beardifierOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Beardifier optimizations"), this.config.beardifierOptimizations, value -> this.config.beardifierOptimizations = value);
        y += 24;
        this.oreVeinOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Ore vein optimizations"), this.config.oreVeinOptimizations, value -> this.config.oreVeinOptimizations = value);
        y += 24;

        y = this.addSectionHeader("Compatibility", y);
        this.fastLegacyRandomButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Fast legacy random"), this.config.fastLegacyRandom, value -> this.config.fastLegacyRandom = value);
        y += 24;

        y = this.addSectionHeader("Diagnostics", y);
        this.debugLoggingButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Debug logging"), this.config.debugLogging, value -> this.config.debugLogging = value);
        y += 24;

        this.doneButton = this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> this.onClose()).bounds(centerX - 50, this.height - 26, 100, 20).build());
        this.contentBottom = y;
        this.maxScroll = Math.max(0, y - (this.doneButton.getY() - 20));
        this.applyScroll();
        this.updateButtonStates();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.updateAnimatedScroll();
        this.renderBackground(guiGraphics);
        int topPanelBottom = 32 + this.warningLines().size() * 10 + 18;
        int contentBottom = this.doneButton.getY() - 6;
        this.renderTiledPanel(guiGraphics, 0, 0, this.width, topPanelBottom);
        this.renderTiledPanel(guiGraphics, 0, contentBottom, this.width, this.height - contentBottom);
        this.renderContentShade(guiGraphics, topPanelBottom, contentBottom);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
        int warningY = 30;
        for (WarningLine warningLine : this.warningLines()) {
            guiGraphics.drawCenteredString(this.font, warningLine.message(), this.width / 2, warningY, warningLine.color());
            warningY += 10;
        }
        guiGraphics.enableScissor(0, topPanelBottom, this.width, contentBottom);
        for (ScrollableLabel label : this.scrollableLabels) {
            int y = label.baseY() - this.scrollOffset;
            int height = label.centered() ? 16 : 20;
            if (this.isPartiallyVisible(y, height)) {
                if (label.centered()) {
                    guiGraphics.drawCenteredString(this.font, label.message(), this.width / 2, y + 4, 0xFF59BD);
                } else {
                    guiGraphics.drawString(this.font, label.message(), label.x(), y + 6, 16777215, false);
                }
            }
        }
        boolean doneButtonVisible = this.doneButton.visible;
        this.doneButton.visible = false;
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.doneButton.visible = doneButtonVisible;
        guiGraphics.disableScissor();
        this.doneButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderScrollBar(guiGraphics, topPanelBottom, contentBottom);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.maxScroll <= 0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        this.targetScrollOffset = Math.max(0, Math.min(this.maxScroll, this.targetScrollOffset - (int) Math.signum(delta) * 16));
        return true;
    }

    private Button createToggleButton(int x, int y, int width, boolean initialValue, BooleanConsumer consumer) {
        return Button.builder(this.toggleLabel(initialValue), button -> {
            boolean nextValue = !this.currentValue(button.getMessage());
            consumer.accept(nextValue);
            button.setMessage(this.toggleLabel(nextValue));
            this.saveConfig();
            this.updateButtonStates();
        }).bounds(x, y, width, 20).build();
    }

    private Button addToggleRow(int labelX, int buttonX, int y, int buttonWidth, Component label, boolean initialValue, BooleanConsumer consumer) {
        this.scrollableLabels.add(new ScrollableLabel(label, labelX, y, false));
        return this.addScrollableWidget(this.createToggleButton(buttonX, y, buttonWidth, initialValue, consumer), y);
    }

    private int addSectionHeader(String title, int y) {
        this.scrollableLabels.add(new ScrollableLabel(Component.literal(title), 0, y, true));
        return y + 16;
    }

    private Component toggleLabel(boolean value) {
        return Component.literal(value ? "On" : "Off");
    }

    private boolean currentValue(Component message) {
        return message.getString().equalsIgnoreCase("On");
    }

    private void saveConfig() {
        AdrenalineConfig.save(new AdrenalineConfig.Data(this.config));
    }

    private <T extends AbstractWidget> T addScrollableWidget(T widget, int baseY) {
        this.scrollableWidgets.add(new ScrollableWidget(widget, baseY));
        return this.addRenderableWidget(widget);
    }

    private void applyScroll() {
        for (ScrollableWidget scrollableWidget : this.scrollableWidgets) {
            int y = scrollableWidget.baseY() - this.scrollOffset;
            scrollableWidget.widget().setY(y);
            scrollableWidget.widget().visible = this.isPartiallyVisible(y, scrollableWidget.widget().getHeight());
        }
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
        this.applyScroll();
    }

    private boolean isPartiallyVisible(int y, int height) {
        int top = 32;
        int bottom = this.doneButton.getY() - 12;
        return y + height >= top && y <= bottom;
    }

    private void renderScrollBar(GuiGraphics guiGraphics, int top, int bottom) {
        if (this.maxScroll <= 0) {
            return;
        }

        int trackHeight = bottom - top;
        int barX = this.width - 10;
        int totalContentHeight = Math.max(trackHeight, this.contentBottom - 32);
        int thumbHeight = Math.max(24, trackHeight * trackHeight / totalContentHeight);
        int travel = trackHeight - thumbHeight;
        int thumbY = top + (travel * this.scrollOffset / this.maxScroll);

        guiGraphics.fill(barX, top, barX + 4, bottom, 0xFF3A3A3A);
        guiGraphics.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, 0xFFA0A0A0);
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

    private void renderContentShade(GuiGraphics guiGraphics, int top, int bottom) {
        guiGraphics.fill(0, top, this.width, bottom, 0x55000000);
    }

    private void updateButtonStates() {
        boolean worldgenOptimizations = this.config.worldgenOptimizations;
        boolean parallelWorldgen = this.config.parallelWorldgen;

        this.worldgenOptimizationsButton.active = true;
        this.terrainFillOptimizationsButton.active = worldgenOptimizations;
        this.surfaceOptimizationsButton.active = worldgenOptimizations;
        this.noiseChunkOptimizationsButton.active = worldgenOptimizations;
        this.materialRuleOptimizationsButton.active = worldgenOptimizations;
        this.aquiferOptimizationsButton.active = worldgenOptimizations;
        this.beardifierOptimizationsButton.active = worldgenOptimizations;
        this.oreVeinOptimizationsButton.active = worldgenOptimizations;
        this.parallelWorldgenButton.active = true;
        this.fastLegacyRandomButton.active = true;
        this.debugLoggingButton.active = true;
        this.workerThreadsSlider.active = parallelWorldgen;
        this.spawnZoneRadiusSlider.active = true;
    }

    private List<WarningLine> warningLines() {
        List<WarningLine> warnings = new ArrayList<>();
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.level == null) {
            return warnings;
        }

        if (!minecraft.hasSingleplayerServer()) {
            warnings.add(new WarningLine(Component.literal("This is not local world, changes won't affect anything"), 16755200));
            return warnings;
        }

        if (minecraft.getSingleplayerServer() != null && minecraft.getSingleplayerServer().isPublished()) {
            warnings.add(new WarningLine(Component.literal("This world is open to LAN, changes may not affect current session"), 16755200));
        }

        warnings.add(new WarningLine(Component.literal("Restart or re-enter the world for safest results"), 11184810));
        return warnings;
    }

    private final class WorkerThreadsSlider extends AbstractSliderButton {

        private WorkerThreadsSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), AdrenalineConfigScreen.this.toSliderValue(AdrenalineConfigScreen.this.config.workerThreads));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = AdrenalineConfigScreen.this.snapSliderValue(this.value);
            this.setMessage(Component.literal("Worker threads: " + (value == 0 ? "Auto" : Integer.toString(value))));
        }

        @Override
        protected void applyValue() {
            int snappedValue = AdrenalineConfigScreen.this.snapSliderValue(this.value);
            this.value = AdrenalineConfigScreen.this.toSliderValue(snappedValue);
            AdrenalineConfigScreen.this.config.workerThreads = snappedValue;
            this.updateMessage();
            AdrenalineConfigScreen.this.saveConfig();
            AdrenalineConfigScreen.this.updateButtonStates();
        }
    }

    private final class SpawnZoneRadiusSlider extends AbstractSliderButton {

        private SpawnZoneRadiusSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), AdrenalineConfigScreen.this.toSpawnZoneRadiusSliderValue(AdrenalineConfigScreen.this.config.spawnZoneRadius));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = AdrenalineConfigScreen.this.snapSpawnZoneRadiusSliderValue(this.value);
            this.setMessage(Component.literal(value == 0 ? "Spawn zone radius: Default" : "Spawn zone radius: " + value));
        }

        @Override
        protected void applyValue() {
            int snappedValue = AdrenalineConfigScreen.this.snapSpawnZoneRadiusSliderValue(this.value);
            this.value = AdrenalineConfigScreen.this.toSpawnZoneRadiusSliderValue(snappedValue);
            AdrenalineConfigScreen.this.config.spawnZoneRadius = snappedValue;
            this.updateMessage();
            AdrenalineConfigScreen.this.saveConfig();
            AdrenalineConfigScreen.this.updateButtonStates();
        }
    }

    private double toSliderValue(int workerThreads) {
        int clamped = Math.max(0, Math.min(workerThreads, this.availableProcessors));
        return this.availableProcessors <= 0 ? 0.0D : (double) clamped / (double) this.availableProcessors;
    }

    private int fromSliderValue(double value) {
        int resolved = (int) Math.round(value * this.availableProcessors);
        if (resolved <= 0) {
            return 0;
        }
        return Math.min(resolved, this.availableProcessors);
    }

    private int snapSliderValue(double value) {
        return this.fromSliderValue(value);
    }

    private double toSpawnZoneRadiusSliderValue(int spawnZoneRadius) {
        if (spawnZoneRadius <= 0) {
            return 0.0D;
        }
        int clamped = Math.max(12, Math.min(spawnZoneRadius, 30));
        return (double) (clamped - 11) / 19.0D;
    }

    private int fromSpawnZoneRadiusSliderValue(double value) {
        int resolved = 11 + (int) Math.round(value * 19.0D);
        if (resolved <= 11) {
            return 0;
        }
        return Math.min(resolved, 30);
    }

    private int snapSpawnZoneRadiusSliderValue(double value) {
        return this.fromSpawnZoneRadiusSliderValue(value);
    }

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private record WarningLine(Component message, int color) {
    }

    private record ScrollableWidget(AbstractWidget widget, int baseY) {
    }

    private record ScrollableLabel(Component message, int x, int baseY, boolean centered) {
    }
}
