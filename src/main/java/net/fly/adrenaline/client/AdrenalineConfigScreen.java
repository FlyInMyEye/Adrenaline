package net.fly.adrenaline.client;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class AdrenalineConfigScreen extends Screen {

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("textures/gui/options_background.png");

    private final Screen parent;
    private final AdrenalineConfig.Data config;
    private final int availableProcessors;
    private final List<ScrollableWidget> scrollableWidgets = new ArrayList<>();
    private final List<ScrollableLabel> scrollableLabels = new ArrayList<>();
    private final Map<AbstractWidget, TooltipData> widgetTooltips = new HashMap<>();

    private ThreadCountSlider generationThreadsSlider;
    private ThreadCountSlider serializationThreadsSlider;
    private SpawnZoneRadiusSlider spawnZoneRadiusSlider;
    private Button worldgenOptimizationsButton;
    private Button terrainFillOptimizationsButton;
    private Button surfaceOptimizationsButton;
    private Button noiseChunkOptimizationsButton;
    private Button materialRuleOptimizationsButton;
    private Button aquiferOptimizationsButton;
    private Button beardifierOptimizationsButton;
    private Button oreVeinOptimizationsButton;
    private Button initialSpawnOptimizationButton;
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
        this.widgetTooltips.clear();
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
        this.generationThreadsSlider = this.addScrollableWidget(new ThreadCountSlider(leftX, y, 370, 20, "Generation threads", () -> this.config.generationWorkerThreads, value -> this.config.generationWorkerThreads = value, tooltip("Controls parallel chunk generation workers.", PerformanceImpact.HIGH)), y, tooltip("Controls parallel chunk generation workers.", PerformanceImpact.HIGH));
        y += 30;
        this.serializationThreadsSlider = this.addScrollableWidget(new ThreadCountSlider(leftX, y, 370, 20, "Serialization threads", () -> this.config.serializationWorkerThreads, value -> this.config.serializationWorkerThreads = value, tooltip("Controls parallel chunk save encoding workers.", PerformanceImpact.MEDIUM)), y, tooltip("Controls parallel chunk save encoding workers.", PerformanceImpact.MEDIUM));
        y += 30;
        this.spawnZoneRadiusSlider = this.addScrollableWidget(new SpawnZoneRadiusSlider(leftX, y, 370, 20, tooltip("Changes the chunk radius generated around spawn.", PerformanceImpact.LOW)), y, tooltip("Changes the chunk radius generated around spawn.", PerformanceImpact.LOW));
        y += 30;
        this.worldgenOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Worldgen optimization"), this.config.worldgenOptimizations, value -> this.config.worldgenOptimizations = value, tooltip("Master switch for Adrenaline world generation changes.", PerformanceImpact.EXTREME));
        y += 24;
        this.parallelWorldgenButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Parallel worldgen"), this.config.parallelWorldgen, value -> this.config.parallelWorldgen = value, tooltip("Runs chunk generation work on the generation pool.", PerformanceImpact.HIGH));
        y += 24;

        y = this.addSectionHeader("Worldgen optimization", y);
        this.terrainFillOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Terrain fill optimizations"), this.config.terrainFillOptimizations, value -> this.config.terrainFillOptimizations = value, tooltip("Speeds up block filling during noise terrain generation.", PerformanceImpact.EXTREME));
        y += 24;
        this.surfaceOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Surface optimizations"), this.config.surfaceOptimizations, value -> this.config.surfaceOptimizations = value, tooltip("Speeds up surface rule evaluation and block placement.", PerformanceImpact.HIGH));
        y += 24;
        this.noiseChunkOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Noise chunk optimizations"), this.config.noiseChunkOptimizations, value -> this.config.noiseChunkOptimizations = value, tooltip("Optimizes hot paths inside noise chunk sampling.", PerformanceImpact.HIGH));
        y += 24;
        this.materialRuleOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Material rule optimizations"), this.config.materialRuleOptimizations, value -> this.config.materialRuleOptimizations = value, tooltip("Reduces overhead in material rule dispatch.", PerformanceImpact.MEDIUM));
        y += 24;
        this.aquiferOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Aquifer optimizations"), this.config.aquiferOptimizations, value -> this.config.aquiferOptimizations = value, tooltip("Reduces aquifer lookup and fluid decision cost.", PerformanceImpact.MEDIUM));
        y += 24;
        this.beardifierOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Beardifier optimizations"), this.config.beardifierOptimizations, value -> this.config.beardifierOptimizations = value, tooltip("Speeds up structure terrain blending calculations.", PerformanceImpact.LOW));
        y += 24;
        this.oreVeinOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Ore vein optimizations"), this.config.oreVeinOptimizations, value -> this.config.oreVeinOptimizations = value, tooltip("Speeds up ore vein sampling during generation.", PerformanceImpact.LOW));
        y += 24;
        this.initialSpawnOptimizationButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Initial spawn optimization"), this.config.initialSpawnOptimization, value -> this.config.initialSpawnOptimization = value, tooltip("Skips vanilla's slow initial spawn refinement and jumps directly into normal start-region generation.", PerformanceImpact.HIGH));
        y += 24;

        y = this.addSectionHeader("Compatibility", y);
        this.fastLegacyRandomButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Fast legacy random"), this.config.fastLegacyRandom, value -> this.config.fastLegacyRandom = value, tooltip("Replaces legacy random with a faster implementation.", PerformanceImpact.LOW));
        y += 24;

        y = this.addSectionHeader("Diagnostics", y);
        this.debugLoggingButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.literal("Debug logging"), this.config.debugLogging, value -> this.config.debugLogging = value, tooltip("Logs extra Adrenaline diagnostics to the console.", PerformanceImpact.NONE));
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
        this.renderTooltip(guiGraphics, mouseX, mouseY);
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

    private Button addToggleRow(int labelX, int buttonX, int y, int buttonWidth, Component label, boolean initialValue, BooleanConsumer consumer, TooltipData tooltip) {
        this.scrollableLabels.add(new ScrollableLabel(label, labelX, y, false, tooltip));
        return this.addScrollableWidget(this.createToggleButton(buttonX, y, buttonWidth, initialValue, consumer), y, tooltip);
    }

    private int addSectionHeader(String title, int y) {
        this.scrollableLabels.add(new ScrollableLabel(Component.literal(title), 0, y, true, null));
        return y + 16;
    }

    private Component toggleLabel(boolean value) {
        int color = value ? 0x55FF55 : 0xFF5555;
        return Component.literal(value ? "On" : "Off").withStyle(style -> style.withColor(color));
    }

    private boolean currentValue(Component message) {
        return message.getString().equalsIgnoreCase("On");
    }

    private void saveConfig() {
        AdrenalineConfig.save(new AdrenalineConfig.Data(this.config));
    }

    private <T extends AbstractWidget> T addScrollableWidget(T widget, int baseY, TooltipData tooltip) {
        this.scrollableWidgets.add(new ScrollableWidget(widget, baseY));
        this.widgetTooltips.put(widget, tooltip);
        return this.addRenderableWidget(widget);
    }

    private void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        TooltipData tooltip = this.findTooltip(mouseX, mouseY);
        if (tooltip != null) {
            guiGraphics.renderComponentTooltip(this.font, tooltip.lines(), mouseX, mouseY);
        }
    }

    private TooltipData findTooltip(int mouseX, int mouseY) {
        for (ScrollableWidget scrollableWidget : this.scrollableWidgets) {
            AbstractWidget widget = scrollableWidget.widget();
            if (!widget.visible) {
                continue;
            }
            TooltipData tooltip = this.widgetTooltips.get(widget);
            if (tooltip != null && widget.isMouseOver(mouseX, mouseY)) {
                return tooltip;
            }
        }

        for (ScrollableLabel label : this.scrollableLabels) {
            if (label.tooltip() == null) {
                continue;
            }
            int y = label.baseY() - this.scrollOffset;
            int height = label.centered() ? 16 : 20;
            if (!this.isPartiallyVisible(y, height)) {
                continue;
            }

            int x = label.centered() ? this.width / 2 - this.font.width(label.message()) / 2 : label.x();
            int width = this.font.width(label.message());
            if (mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
                return label.tooltip();
            }
        }

        return null;
    }

    private static TooltipData tooltip(String description, PerformanceImpact impact) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(description));
        lines.add(CommonComponents.EMPTY);
        lines.add(CommonComponents.EMPTY);
        MutableComponent impactLine = Component.literal("Performance impact: ");
        impactLine.append(Component.literal(impact.label).withStyle(style -> style.withColor(impact.color)));
        lines.add(impactLine);
        return new TooltipData(lines);
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
        this.initialSpawnOptimizationButton.active = worldgenOptimizations;
        this.parallelWorldgenButton.active = true;
        this.fastLegacyRandomButton.active = true;
        this.debugLoggingButton.active = true;
        this.generationThreadsSlider.active = worldgenOptimizations;
        this.serializationThreadsSlider.active = worldgenOptimizations;
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

    private final class ThreadCountSlider extends AbstractSliderButton {

        private final String label;
        private final IntConsumer setter;
        private final TooltipData tooltip;

        private ThreadCountSlider(int x, int y, int width, int height, String label, IntSupplier getter, IntConsumer setter, TooltipData tooltip) {
            super(x, y, width, height, Component.empty(), AdrenalineConfigScreen.this.toSliderValue(getter.getAsInt()));
            this.label = label;
            this.setter = setter;
            this.tooltip = tooltip;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = AdrenalineConfigScreen.this.snapSliderValue(this.value);
            this.setMessage(Component.literal(this.label + ": " + (value == 0 ? "Auto" : Integer.toString(value))));
        }

        @Override
        protected void applyValue() {
            int snappedValue = AdrenalineConfigScreen.this.snapSliderValue(this.value);
            this.value = AdrenalineConfigScreen.this.toSliderValue(snappedValue);
            this.setter.accept(snappedValue);
            this.updateMessage();
            AdrenalineConfigScreen.this.saveConfig();
            AdrenalineConfigScreen.this.updateButtonStates();
        }
    }

    private final class SpawnZoneRadiusSlider extends AbstractSliderButton {

        private final TooltipData tooltip;

        private SpawnZoneRadiusSlider(int x, int y, int width, int height, TooltipData tooltip) {
            super(x, y, width, height, Component.empty(), AdrenalineConfigScreen.this.toSpawnZoneRadiusSliderValue(AdrenalineConfigScreen.this.config.spawnZoneRadius));
            this.tooltip = tooltip;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = AdrenalineConfigScreen.this.snapSpawnZoneRadiusSliderValue(this.value);
            if (value == AdrenalineConfig.DEFAULT_SPAWN_ZONE_RADIUS) {
                this.setMessage(Component.literal("Spawn zone radius: Default"));
                return;
            }
            this.setMessage(Component.literal(value == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? "Spawn zone radius: Instant" : "Spawn zone radius: " + value));
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
        int clamped = Math.max(AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS, Math.min(spawnZoneRadius, AdrenalineConfig.MAX_SPAWN_ZONE_RADIUS));
        int span = AdrenalineConfig.MAX_SPAWN_ZONE_RADIUS - AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS;
        return span <= 0 ? 0.0D : (double) (clamped - AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) / (double) span;
    }

    private int fromSpawnZoneRadiusSliderValue(double value) {
        int span = AdrenalineConfig.MAX_SPAWN_ZONE_RADIUS - AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS;
        return Math.min(AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS + (int) Math.round(value * span), AdrenalineConfig.MAX_SPAWN_ZONE_RADIUS);
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

    private record TooltipData(List<Component> lines) {
    }

    private record ScrollableWidget(AbstractWidget widget, int baseY) {
    }

    private record ScrollableLabel(Component message, int x, int baseY, boolean centered, TooltipData tooltip) {
    }

    private enum PerformanceImpact {
        NONE("None", 0xAAAAAA),
        LOW("Low", 0x55FF55),
        MEDIUM("Medium", 0xFFFF55),
        HIGH("High", 0xFF5555),
        EXTREME("Extreme", 0xAA00AA);

        private final String label;
        private final int color;

        PerformanceImpact(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }
}
