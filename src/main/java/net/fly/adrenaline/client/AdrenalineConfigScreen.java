package net.fly.adrenaline.client;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AdrenalineConfigScreen extends Screen {

    private final Screen parent;
    private final AdrenalineConfig.Data config;
    private final int availableProcessors;

    private WorkerThreadsSlider workerThreadsSlider;
    private Button parallelWorldgenButton;
    private Button parallelChunkSerializationButton;
    private Button chunkIoCacheButton;
    private Button fastLegacyRandomButton;
    private Button preloadProblematicClassesButton;

    public AdrenalineConfigScreen(Screen parent) {
        super(Component.literal("Adrenaline"));
        this.parent = parent;
        this.config = AdrenalineConfig.copy();
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 40;

        this.parallelWorldgenButton = this.addRenderableWidget(this.createToggleButton(centerX - 100, y, Component.literal("Parallel worldgen"), this.config.parallelWorldgen, value -> this.config.parallelWorldgen = value));
        y += 24;
        this.parallelChunkSerializationButton = this.addRenderableWidget(this.createToggleButton(centerX - 100, y, Component.literal("Parallel chunk serialization"), this.config.parallelChunkSerialization, value -> this.config.parallelChunkSerialization = value));
        y += 24;
        this.chunkIoCacheButton = this.addRenderableWidget(this.createToggleButton(centerX - 100, y, Component.literal("Chunk IO cache"), this.config.chunkIoCache, value -> this.config.chunkIoCache = value));
        y += 24;
        this.fastLegacyRandomButton = this.addRenderableWidget(this.createToggleButton(centerX - 100, y, Component.literal("Fast legacy random"), this.config.fastLegacyRandom, value -> this.config.fastLegacyRandom = value));
        y += 24;
        this.preloadProblematicClassesButton = this.addRenderableWidget(this.createToggleButton(centerX - 100, y, Component.literal("Preload problematic classes"), this.config.preloadProblematicClasses, value -> this.config.preloadProblematicClasses = value));
        y += 30;

        this.workerThreadsSlider = this.addRenderableWidget(new WorkerThreadsSlider(centerX - 100, y, 200, 20));
        y += 32;

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> this.save()).bounds(centerX - 100, y, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose()).bounds(centerX + 2, y, 98, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
        guiGraphics.drawCenteredString(this.font, "Changes apply to new worldgen work. Restarting the world is safest.", this.width / 2, 28, 11184810);
        guiGraphics.drawString(this.font, "Worker threads", this.width / 2 - 100, this.workerThreadsSlider.getY() - 12, 16777215, false);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private Button createToggleButton(int x, int y, Component label, boolean initialValue, BooleanConsumer consumer) {
        return Button.builder(this.toggleLabel(label, initialValue), button -> {
            boolean nextValue = !this.currentValue(button.getMessage());
            consumer.accept(nextValue);
            button.setMessage(this.toggleLabel(label, nextValue));
        }).bounds(x, y, 200, 20).build();
    }

    private Component toggleLabel(Component label, boolean value) {
        return Component.literal(label.getString() + ": " + (value ? "ON" : "OFF"));
    }

    private boolean currentValue(Component message) {
        return message.getString().endsWith("ON");
    }

    private void save() {
        AdrenalineConfig.save(new AdrenalineConfig.Data(this.config));
        this.onClose();
    }

    private final class WorkerThreadsSlider extends AbstractSliderButton {

        private WorkerThreadsSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), AdrenalineConfigScreen.this.toSliderValue(AdrenalineConfigScreen.this.config.workerThreads));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = AdrenalineConfigScreen.this.snapSliderValue(this.value);
            this.setMessage(Component.literal(value == 0 ? "Auto" : Integer.toString(value)));
        }

        @Override
        protected void applyValue() {
            int snappedValue = AdrenalineConfigScreen.this.snapSliderValue(this.value);
            this.value = AdrenalineConfigScreen.this.toSliderValue(snappedValue);
            AdrenalineConfigScreen.this.config.workerThreads = snappedValue;
            this.updateMessage();
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

    @FunctionalInterface
    private interface BooleanConsumer {
        void accept(boolean value);
    }
}
