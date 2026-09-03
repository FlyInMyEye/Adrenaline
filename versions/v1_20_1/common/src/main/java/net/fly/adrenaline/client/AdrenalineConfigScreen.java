package net.fly.adrenaline.client;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.AdrenalineNatives;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
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
    private final List<Button> stageParallelButtons = new ArrayList<>();

    private ThreadCountSlider generationThreadsSlider;
    private ThreadCountSlider serializationThreadsSlider;
    private SpawnZoneRadiusSlider spawnZoneRadiusSlider;
    private FeatureSafetyRadiusSlider featureSafetyRadiusSlider;
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
    private Button stagePriorityButton;
    private Button saveChunksAfterWorldCreationButton;
    private Button incrementalSaveIntervalButton;
    private Button skipSavingScreenAfterExitButton;
    private Button fastLegacyRandomButton;
    private Button nativePerlinBatchingButton;
    private Button approximateNativePerlinButton;
    private Button nativeDensityEvaluationButton;
    private Button nativeAquiferBatchingButton;
    private Button showCancelButton;
    private Button showChunkPreviewButton;
    private Button showThreadVisualizerButton;
    private Button warmupOnStartupButton;
    private Button prepareWorldCreationContextButton;
    private Button fastTerrainLoadingButton;
    private Button startBeforehandButton;
    private Button worldgenBenchmarkButton;
    private Button debugLoggingButton;
    private Button forceEasterEggButton;
    private Button doneButton;
    private int scrollOffset;
    private int targetScrollOffset;
    private int maxScroll;
    private double animatedScrollOffset;
    private boolean scrolling;

    public AdrenalineConfigScreen(Screen parent) {
        super(Component.translatable("gui.adrenaline.config.title"));
        this.parent = parent;
        this.config = AdrenalineConfig.copy();
        this.availableProcessors = Runtime.getRuntime().availableProcessors();
    }

    @Override
    protected void init() {
        this.scrollableWidgets.clear();
        this.scrollableLabels.clear();
        this.widgetTooltips.clear();
        this.stageParallelButtons.clear();
        this.scrollOffset = 0;
        this.targetScrollOffset = 0;
        this.animatedScrollOffset = 0.0D;
        this.scrolling = false;
        int centerX = this.width / 2;
        int y = 54 + this.warningLines().size() * 10;
        int leftX = centerX - 185;
        int buttonX = centerX + 95;
        int labelX = leftX;
        int buttonWidth = 70;

        y = this.addSectionHeader("gui.adrenaline.config.section.general", y);
        this.generationThreadsSlider = this.addScrollableWidget(new ThreadCountSlider(leftX, y, 370, 20, Component.translatable("gui.adrenaline.config.generation_threads"), () -> this.config.generationWorkerThreads, value -> this.config.generationWorkerThreads = value, tooltip("tooltip.adrenaline.config.generation_threads", PerformanceImpact.HIGH)), y, tooltip("tooltip.adrenaline.config.generation_threads", PerformanceImpact.HIGH));
        y += 30;
        TooltipData spawnZoneTooltip = optimizationTooltip("tooltip.adrenaline.config.spawn_zone_radius", PerformanceImpact.LOW, Optimization.SPAWN_ZONE);
        this.spawnZoneRadiusSlider = this.addScrollableWidget(new SpawnZoneRadiusSlider(leftX, y, 370, 20, spawnZoneTooltip), y, spawnZoneTooltip);
        y += 30;
        this.worldgenOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.worldgen_optimization"), this.config.worldgenOptimizations, value -> this.config.worldgenOptimizations = value, tooltip("tooltip.adrenaline.config.worldgen_optimization", PerformanceImpact.EXTREME));
        y += 24;
        this.parallelWorldgenButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.parallel_worldgen"), this.config.parallelWorldgen, value -> this.config.parallelWorldgen = value, optimizationTooltip("tooltip.adrenaline.config.parallel_worldgen", PerformanceImpact.HIGH, Optimization.PARALLEL_WORLDGEN));
        y += 24;
        this.stagePriorityButton = this.addStagePriorityRow(labelX, buttonX, y, buttonWidth, tooltip("tooltip.adrenaline.config.stage_priority", PerformanceImpact.MEDIUM));
        y += 24;

        y = this.addSectionHeader("gui.adrenaline.config.section.features_plushies", y);
        this.showCancelButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.show_cancel_button"), this.config.showCancelButton, value -> this.config.showCancelButton = value, tooltip("tooltip.adrenaline.config.show_cancel_button", PerformanceImpact.NONE));
        y += 24;
        this.showChunkPreviewButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.show_chunk_preview"), this.config.showChunkPreview, value -> this.config.showChunkPreview = value, tooltip("tooltip.adrenaline.config.show_chunk_preview", PerformanceImpact.LOW));
        y += 24;
        this.warmupOnStartupButton = this.addWarmupModeRow(labelX, buttonX, y, buttonWidth, tooltip("tooltip.adrenaline.config.warmup_on_startup", PerformanceImpact.MEDIUM));
        y += 24;
        this.prepareWorldCreationContextButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.prepare_world_creation_context"), this.config.prepareWorldCreationContext, value -> this.config.prepareWorldCreationContext = value, tooltip("tooltip.adrenaline.config.prepare_world_creation_context", PerformanceImpact.LOW));
        y += 24;
        this.fastTerrainLoadingButton = this.addFastTerrainLoadingRow(labelX, buttonX, y, buttonWidth, tooltip("tooltip.adrenaline.config.fast_terrain_loading", PerformanceImpact.LOW));
        y += 24;
        this.startBeforehandButton = this.addStartBeforehandRow(labelX, buttonX, y, buttonWidth, tooltip("tooltip.adrenaline.config.start_beforehand", PerformanceImpact.NONE));
        y += 24;

        y = this.addSectionHeader("gui.adrenaline.config.section.chunk_saving", y);
        this.serializationThreadsSlider = this.addScrollableWidget(new ThreadCountSlider(leftX, y, 370, 20, Component.translatable("gui.adrenaline.config.serialization_threads"), () -> this.config.serializationWorkerThreads, value -> this.config.serializationWorkerThreads = value, tooltip("tooltip.adrenaline.config.serialization_threads", PerformanceImpact.MEDIUM)), y, tooltip("tooltip.adrenaline.config.serialization_threads", PerformanceImpact.MEDIUM));
        y += 30;
        this.saveChunksAfterWorldCreationButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.save_chunks_after_world_creation"), this.config.saveChunksAfterWorldCreation, value -> this.config.saveChunksAfterWorldCreation = value, tooltip("tooltip.adrenaline.config.save_chunks_after_world_creation", PerformanceImpact.MEDIUM));
        y += 24;
        this.incrementalSaveIntervalButton = this.addIncrementalSaveIntervalRow(labelX, buttonX, y, buttonWidth, tooltip("tooltip.adrenaline.config.incremental_save_interval", PerformanceImpact.MEDIUM));
        y += 24;
        this.skipSavingScreenAfterExitButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.skip_saving_screen_after_exit"), this.config.skipSavingScreenAfterExit, value -> this.config.skipSavingScreenAfterExit = value, optimizationTooltip("tooltip.adrenaline.config.skip_saving_screen_after_exit", PerformanceImpact.NONE, Optimization.BACKGROUND_SAVE));
        y += 24;

        y = this.addSectionHeader("gui.adrenaline.config.section.worldgen", y);
        this.terrainFillOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.terrain_fill_optimizations"), this.config.terrainFillOptimizations, value -> this.config.terrainFillOptimizations = value, optimizationTooltip("tooltip.adrenaline.config.terrain_fill_optimizations", PerformanceImpact.EXTREME, Optimization.TERRAIN_FILL));
        y += 24;
        this.surfaceOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.surface_optimizations"), this.config.surfaceOptimizations, value -> this.config.surfaceOptimizations = value, optimizationTooltip("tooltip.adrenaline.config.surface_optimizations", PerformanceImpact.HIGH, Optimization.SURFACE));
        y += 24;
        this.noiseChunkOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.noise_chunk_optimizations"), this.config.noiseChunkOptimizations, value -> this.config.noiseChunkOptimizations = value, optimizationTooltip("tooltip.adrenaline.config.noise_chunk_optimizations", PerformanceImpact.HIGH, Optimization.NOISE_CHUNK));
        y += 24;
        this.materialRuleOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.material_rule_optimizations"), this.config.materialRuleOptimizations, value -> this.config.materialRuleOptimizations = value, optimizationTooltip("tooltip.adrenaline.config.material_rule_optimizations", PerformanceImpact.MEDIUM, Optimization.MATERIAL_RULE));
        y += 24;
        this.aquiferOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.aquifer_optimizations"), this.config.aquiferOptimizations, value -> this.config.aquiferOptimizations = value, optimizationTooltip("tooltip.adrenaline.config.aquifer_optimizations", PerformanceImpact.MEDIUM, Optimization.AQUIFER));
        y += 24;
        this.beardifierOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.beardifier_optimizations"), this.config.beardifierOptimizations, value -> this.config.beardifierOptimizations = value, optimizationTooltip("tooltip.adrenaline.config.beardifier_optimizations", PerformanceImpact.LOW, Optimization.BEARDIFIER));
        y += 24;
        this.oreVeinOptimizationsButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.ore_vein_optimizations"), this.config.oreVeinOptimizations, value -> this.config.oreVeinOptimizations = value, optimizationTooltip("tooltip.adrenaline.config.ore_vein_optimizations", PerformanceImpact.LOW, Optimization.ORE_VEIN));
        y += 24;
        this.initialSpawnOptimizationButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.initial_spawn_optimization"), this.config.initialSpawnOptimization, value -> this.config.initialSpawnOptimization = value, optimizationTooltip("tooltip.adrenaline.config.initial_spawn_optimization", PerformanceImpact.HIGH, Optimization.INITIAL_SPAWN));
        y += 24;

        y = this.addSectionHeader("gui.adrenaline.config.section.natives", y);
        this.nativePerlinBatchingButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.native_perlin_batching"), this.config.nativePerlinBatching, value -> this.config.nativePerlinBatching = value, tooltip("tooltip.adrenaline.config.native_perlin_batching", PerformanceImpact.HIGH));
        y += 24;
        this.approximateNativePerlinButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.approximate_native_perlin"), this.config.approximateNativePerlin, value -> this.config.approximateNativePerlin = value, tooltip("tooltip.adrenaline.config.approximate_native_perlin", PerformanceImpact.EXTREME));
        y += 24;
        this.nativeDensityEvaluationButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.native_density_evaluation"), this.config.nativeDensityEvaluation, value -> this.config.nativeDensityEvaluation = value, tooltip("tooltip.adrenaline.config.native_density_evaluation", PerformanceImpact.EXTREME));
        y += 24;
        this.nativeAquiferBatchingButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.native_aquifer_batching"), this.config.nativeAquiferBatching, value -> this.config.nativeAquiferBatching = value, tooltip("tooltip.adrenaline.config.native_aquifer_batching", PerformanceImpact.EXTREME));
        y += 24;

        y = this.addSectionHeader("gui.adrenaline.config.section.compatibility", y);
        this.featureSafetyRadiusSlider = this.addScrollableWidget(new FeatureSafetyRadiusSlider(leftX, y, 370, 20, tooltip("tooltip.adrenaline.config.feature_safety_radius", PerformanceImpact.HIGH)), y, tooltip("tooltip.adrenaline.config.feature_safety_radius", PerformanceImpact.HIGH));
        y += 30;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("STRUCTURE_STARTS", 10066329), this.config.parallelizeStructureStarts, value -> this.config.parallelizeStructureStarts = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.structure_starts"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("STRUCTURE_REFERENCES", 6250897), this.config.parallelizeStructureReferences, value -> this.config.parallelizeStructureReferences = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.structure_references"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("BIOMES", 8434258), this.config.parallelizeBiomes, value -> this.config.parallelizeBiomes = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.biomes"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("NOISE", 13750737), this.config.parallelizeNoise, value -> this.config.parallelizeNoise = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.noise"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("SURFACE", 7497737), this.config.parallelizeSurface, value -> this.config.parallelizeSurface = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.surface"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("CARVERS", 3159410), this.config.parallelizeCarvers, value -> this.config.parallelizeCarvers = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.carvers"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("FEATURES", 2213376), this.config.parallelizeFeatures, value -> this.config.parallelizeFeatures = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.features"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("INITIALIZE_LIGHT", 13421772), this.config.parallelizeInitializeLight, value -> this.config.parallelizeInitializeLight = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.initialize_light"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("LIGHT", 16769184), this.config.parallelizeLight, value -> this.config.parallelizeLight = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.light"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("SPAWN", 15884384), this.config.parallelizeSpawn, value -> this.config.parallelizeSpawn = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.spawn"))));
        y += 24;
        this.stageParallelButtons.add(this.addToggleRow(labelX, buttonX, y, buttonWidth, this.stageLabel("FULL", 16777215), this.config.parallelizeFull, value -> this.config.parallelizeFull = value, tooltip("tooltip.adrenaline.config.stage_parallel", PerformanceImpact.LOW, Component.translatable("gui.adrenaline.chunk_status.full"))));
        y += 24;
        this.fastLegacyRandomButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.fast_legacy_random"), this.config.fastLegacyRandom, value -> this.config.fastLegacyRandom = value, optimizationTooltip("tooltip.adrenaline.config.fast_legacy_random", PerformanceImpact.LOW, Optimization.FAST_LEGACY_RANDOM));
        y += 24;

        if (BuildConfig.DEBUG) {
            y = this.addSectionHeader("gui.adrenaline.config.section.diagnostics", y);
            TooltipData benchmarkTooltip = tooltip("tooltip.adrenaline.config.worldgen_benchmark", PerformanceImpact.NONE);
            this.scrollableLabels.add(new ScrollableLabel(Component.translatable("gui.adrenaline.config.worldgen_benchmark"), labelX, y, false, benchmarkTooltip));
            this.worldgenBenchmarkButton = this.addScrollableWidget(Button.builder(Component.translatable("gui.adrenaline.benchmark.run"), button -> {
                this.saveConfig();
                if (WorldgenBenchmark.start(this.minecraft, this.config)) {
                    this.minecraft.setScreen(new WorldgenBenchmarkScreen(this));
                }
            }).bounds(buttonX, y, buttonWidth, 20).build(), y, benchmarkTooltip);
            y += 24;
            this.showThreadVisualizerButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.show_thread_visualizer"), this.config.showThreadVisualizer, value -> this.config.showThreadVisualizer = value, tooltip("tooltip.adrenaline.config.show_thread_visualizer", PerformanceImpact.NONE));
            y += 24;
            this.debugLoggingButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.debug_logging"), this.config.debugLogging, value -> this.config.debugLogging = value, tooltip("tooltip.adrenaline.config.debug_logging", PerformanceImpact.NONE));
            y += 24;
            this.forceEasterEggButton = this.addToggleRow(labelX, buttonX, y, buttonWidth, Component.translatable("gui.adrenaline.config.force_easter_egg"), this.config.forceEasterEgg, value -> this.config.forceEasterEgg = value, tooltip("tooltip.adrenaline.config.force_easter_egg", PerformanceImpact.NONE));
            y += 24;
        }

        this.doneButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).bounds(centerX - 50, this.height - 26, 100, 20).build());
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.maxScroll > 0 && mouseX >= this.scrollbarX() && mouseX < this.scrollbarX() + 6 && mouseY >= this.scrollbarTop() && mouseY <= this.scrollbarBottom()) {
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

    private Button createToggleButton(int x, int y, int width, boolean initialValue, BooleanConsumer consumer) {
        MutableBoolean state = new MutableBoolean(initialValue);
        return Button.builder(this.toggleLabel(state.value), button -> {
            state.value = !state.value;
            consumer.accept(state.value);
            button.setMessage(this.toggleLabel(state.value));
            this.saveConfig();
            this.updateButtonStates();
        }).bounds(x, y, width, 20).build();
    }

    private Button addToggleRow(int labelX, int buttonX, int y, int buttonWidth, Component label, boolean initialValue, BooleanConsumer consumer, TooltipData tooltip) {
        this.scrollableLabels.add(new ScrollableLabel(label, labelX, y, false, tooltip));
        return this.addScrollableWidget(this.createToggleButton(buttonX, y, buttonWidth, initialValue, consumer), y, tooltip);
    }

    private Button addStartBeforehandRow(int labelX, int buttonX, int y, int buttonWidth, TooltipData tooltip) {
        this.scrollableLabels.add(new ScrollableLabel(Component.translatable("gui.adrenaline.config.start_beforehand"), labelX, y, false, tooltip));
        Button button = Button.builder(this.startBeforehandLabel(), pressed -> {
            this.config.startBeforehand = switch (this.config.startBeforehand) {
                case ON -> AdrenalineConfig.StartBeforehandMode.OFF;
                case OFF -> AdrenalineConfig.StartBeforehandMode.BORING;
                case BORING -> AdrenalineConfig.StartBeforehandMode.ON;
            };
            pressed.setMessage(this.startBeforehandLabel());
            this.saveConfig();
        }).bounds(buttonX, y, buttonWidth, 20).build();
        return this.addScrollableWidget(button, y, tooltip);
    }

    private Button addStagePriorityRow(int labelX, int buttonX, int y, int buttonWidth, TooltipData tooltip) {
        this.scrollableLabels.add(new ScrollableLabel(Component.translatable("gui.adrenaline.config.stage_priority"), labelX, y, false, tooltip));
        Button button = Button.builder(this.stagePriorityLabel(), pressed -> {
            AdrenalineConfig.StagePriority priority = switch (this.config.stagePriority()) {
                case FIFO -> AdrenalineConfig.StagePriority.HIGHEST;
                case HIGHEST -> AdrenalineConfig.StagePriority.NEAREST;
                case NEAREST -> AdrenalineConfig.StagePriority.FIFO;
            };
            this.config.setStagePriority(priority);
            pressed.setMessage(this.stagePriorityLabel());
            this.saveConfig();
        }).bounds(buttonX, y, buttonWidth, 20).build();
        return this.addScrollableWidget(button, y, tooltip);
    }

    private Button addIncrementalSaveIntervalRow(int labelX, int buttonX, int y, int buttonWidth, TooltipData tooltip) {
        this.scrollableLabels.add(new ScrollableLabel(Component.translatable("gui.adrenaline.config.incremental_save_interval"), labelX, y, false, tooltip));
        Button button = Button.builder(this.incrementalSaveIntervalLabel(), pressed -> {
            this.config.incrementalSaveInterval = switch (this.config.incrementalSaveInterval) {
                case 0 -> 512;
                case 512 -> 256;
                case 256 -> 128;
                case 128 -> 64;
                case 64 -> 32;
                case 32 -> 0;
                default -> 128;
            };
            pressed.setMessage(this.incrementalSaveIntervalLabel());
            this.saveConfig();
        }).bounds(buttonX, y, buttonWidth, 20).build();
        return this.addScrollableWidget(button, y, tooltip);
    }

    private Button addWarmupModeRow(int labelX, int buttonX, int y, int buttonWidth, TooltipData tooltip) {
        this.scrollableLabels.add(new ScrollableLabel(Component.translatable("gui.adrenaline.config.warmup_on_startup"), labelX, y, false, tooltip));
        Button button = Button.builder(this.warmupModeLabel(), pressed -> {
            AdrenalineConfig.WarmupMode mode = switch (this.config.warmupMode()) {
                case ON -> AdrenalineConfig.WarmupMode.NON_BLOCK;
                case NON_BLOCK -> AdrenalineConfig.WarmupMode.OFF;
                case OFF -> AdrenalineConfig.WarmupMode.ON;
            };
            this.config.setWarmupMode(mode);
            pressed.setMessage(this.warmupModeLabel());
            this.saveConfig();
        }).bounds(buttonX, y, buttonWidth, 20).build();
        return this.addScrollableWidget(button, y, tooltip);
    }

    private Button addFastTerrainLoadingRow(int labelX, int buttonX, int y, int buttonWidth, TooltipData tooltip) {
        this.scrollableLabels.add(new ScrollableLabel(Component.translatable("gui.adrenaline.config.fast_terrain_loading"), labelX, y, false, tooltip));
        Button button = Button.builder(this.fastTerrainLoadingLabel(), pressed -> {
            AdrenalineConfig.FastTerrainLoadingMode mode = switch (this.config.fastTerrainLoadingMode()) {
                case OFF -> AdrenalineConfig.FastTerrainLoadingMode.ON;
                case ON -> AdrenalineConfig.FastTerrainLoadingMode.EXTREME;
                case EXTREME -> AdrenalineConfig.FastTerrainLoadingMode.OFF;
            };
            this.config.setFastTerrainLoadingMode(mode);
            pressed.setMessage(this.fastTerrainLoadingLabel());
            this.saveConfig();
        }).bounds(buttonX, y, buttonWidth, 20).build();
        return this.addScrollableWidget(button, y, tooltip);
    }

    private int addSectionHeader(String title, int y) {
        this.scrollableLabels.add(new ScrollableLabel(Component.translatable(title), 0, y, true, null));
        return y + 16;
    }

    private Component toggleLabel(boolean value) {
        int color = value ? 0x55FF55 : 0xFF5555;
        return Component.translatable(value ? "gui.adrenaline.toggle.on" : "gui.adrenaline.toggle.off").withStyle(style -> style.withColor(color));
    }

    private Component startBeforehandLabel() {
        String key = switch (this.config.startBeforehand) {
            case ON -> "gui.adrenaline.toggle.on";
            case OFF -> "gui.adrenaline.toggle.off";
            case BORING -> "gui.adrenaline.toggle.boring";
        };
        int color = switch (this.config.startBeforehand) {
            case ON -> 0x55FF55;
            case OFF -> 0xFF5555;
            case BORING -> 0xFFFF55;
        };
        return Component.translatable(key).withStyle(style -> style.withColor(color));
    }

    private Component stagePriorityLabel() {
        AdrenalineConfig.StagePriority priority = this.config.stagePriority();
        String key = switch (priority) {
            case FIFO -> "gui.adrenaline.stage_priority.fifo";
            case HIGHEST -> "gui.adrenaline.stage_priority.highest";
            case NEAREST -> "gui.adrenaline.stage_priority.nearest";
        };
        int color = switch (priority) {
            case FIFO -> 0xAAAAAA;
            case HIGHEST -> 0xFFFF55;
            case NEAREST -> 0x55FF55;
        };
        return Component.translatable(key).withStyle(style -> style.withColor(color));
    }

    private Component incrementalSaveIntervalLabel() {
        int interval = this.config.incrementalSaveInterval;
        if (interval == 0) {
            return Component.translatable("gui.adrenaline.toggle.off").withStyle(style -> style.withColor(0xFF5555));
        }
        return Component.literal(Integer.toString(interval)).withStyle(style -> style.withColor(0xFFFF55));
    }

    private Component warmupModeLabel() {
        String key = switch (this.config.warmupMode()) {
            case ON -> "gui.adrenaline.toggle.on";
            case NON_BLOCK -> "gui.adrenaline.toggle.non_block";
            case OFF -> "gui.adrenaline.toggle.off";
        };
        int color = switch (this.config.warmupMode()) {
            case ON -> 0x55FF55;
            case NON_BLOCK -> 0xFFFF55;
            case OFF -> 0xFF5555;
        };
        return Component.translatable(key).withStyle(style -> style.withColor(color));
    }

    private Component fastTerrainLoadingLabel() {
        AdrenalineConfig.FastTerrainLoadingMode mode = this.config.fastTerrainLoadingMode();
        String key = switch (mode) {
            case OFF -> "gui.adrenaline.toggle.off";
            case ON -> "gui.adrenaline.toggle.on";
            case EXTREME -> "gui.adrenaline.toggle.extreme";
        };
        int color = switch (mode) {
            case OFF -> 0xFF5555;
            case ON -> 0x55FF55;
            case EXTREME -> 0xFFFF55;
        };
        return Component.translatable(key).withStyle(style -> style.withColor(color));
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
            TooltipData tooltip = this.widgetTooltips.get(widget);
            if (tooltip != null && this.isWidgetHovered(widget, mouseX, mouseY)) {
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

    private boolean isWidgetHovered(AbstractWidget widget, int mouseX, int mouseY) {
        return widget.visible
            && mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth()
            && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight();
    }

    private static TooltipData tooltip(String description, PerformanceImpact impact, Object... args) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(description, args));
        lines.add(CommonComponents.EMPTY);
        lines.add(CommonComponents.EMPTY);
        lines.add(Component.translatable("tooltip.adrenaline.performance_impact", Component.translatable(impact.key()).withStyle(style -> style.withColor(impact.color))));
        return new TooltipData(lines);
    }

    private static TooltipData optimizationTooltip(String description, PerformanceImpact impact, Optimization optimization) {
        TooltipData tooltip = tooltip(description, impact);
        OptimizationTakeoverRegistry.controller(optimization).ifPresent(controller -> tooltip.lines().add(1, Component.translatableWithFallback("tooltip.adrenaline.config.optimization_controlled", "%s took control of this optimization.", controller).withStyle(style -> style.withColor(0xFFAA00))));
        return tooltip;
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
        int barX = this.scrollbarX();
        int thumbHeight = this.scrollbarThumbHeight(trackHeight);
        int travel = trackHeight - thumbHeight;
        int thumbY = top + (travel * this.scrollOffset / this.maxScroll);

        guiGraphics.fill(barX, top, barX + 6, bottom, 0xFF000000);
        guiGraphics.fill(barX, thumbY, barX + 6, thumbY + thumbHeight, 0xFF808080);
        guiGraphics.fill(barX, thumbY, barX + 5, thumbY + thumbHeight - 1, 0xFFC0C0C0);
    }

    private int scrollbarX() {
        return this.width / 2 + 190;
    }

    private int scrollbarTop() {
        return 32 + this.warningLines().size() * 10 + 18;
    }

    private int scrollbarBottom() {
        return this.doneButton.getY() - 6;
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
        this.applyScroll();
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

        this.worldgenOptimizationsButton.active = true;
        this.terrainFillOptimizationsButton.active = worldgenOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.TERRAIN_FILL);
        this.surfaceOptimizationsButton.active = worldgenOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.SURFACE);
        this.noiseChunkOptimizationsButton.active = worldgenOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.NOISE_CHUNK);
        this.materialRuleOptimizationsButton.active = worldgenOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.MATERIAL_RULE);
        this.aquiferOptimizationsButton.active = worldgenOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.AQUIFER);
        this.beardifierOptimizationsButton.active = worldgenOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.BEARDIFIER);
        this.oreVeinOptimizationsButton.active = worldgenOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.ORE_VEIN);
        this.initialSpawnOptimizationButton.active = worldgenOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.INITIAL_SPAWN);
        this.parallelWorldgenButton.active = !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN);
        this.stagePriorityButton.active = this.config.parallelWorldgen;
        this.saveChunksAfterWorldCreationButton.active = true;
        this.incrementalSaveIntervalButton.active = true;
        this.skipSavingScreenAfterExitButton.active = !OptimizationTakeoverRegistry.isControlled(Optimization.BACKGROUND_SAVE);
        this.featureSafetyRadiusSlider.active = this.config.parallelWorldgen;
        for (Button button : this.stageParallelButtons) {
            button.active = this.config.parallelWorldgen;
        }
        this.fastLegacyRandomButton.active = !OptimizationTakeoverRegistry.isControlled(Optimization.FAST_LEGACY_RANDOM);
        this.nativePerlinBatchingButton.active = AdrenalineNatives.isAvailable();
        this.approximateNativePerlinButton.active = AdrenalineNatives.isAvailable() && this.config.nativePerlinBatching;
        this.nativeDensityEvaluationButton.active = AdrenalineNatives.isAvailable();
        this.nativeAquiferBatchingButton.active = AdrenalineNatives.isAvailable() && worldgenOptimizations && this.config.aquiferOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.AQUIFER);
        this.showCancelButton.active = true;
        this.showChunkPreviewButton.active = true;
        this.startBeforehandButton.active = true;
        if (BuildConfig.DEBUG && this.debugLoggingButton != null) {
            this.worldgenBenchmarkButton.active = this.minecraft.level == null
                && this.minecraft.getSingleplayerServer() == null
                && !WorldgenBenchmark.isRunning()
                && !BackgroundWorldgenWarmup.isRunning()
                && !BackgroundWorldSave.isRunning();
            this.showThreadVisualizerButton.active = true;
            this.debugLoggingButton.active = true;
            this.forceEasterEggButton.active = true;
        }
        this.generationThreadsSlider.active = this.config.parallelWorldgen;
        this.serializationThreadsSlider.active = worldgenOptimizations;
        this.spawnZoneRadiusSlider.active = !OptimizationTakeoverRegistry.isControlled(Optimization.SPAWN_ZONE);
    }

    private List<WarningLine> warningLines() {
        List<WarningLine> warnings = new ArrayList<>();
        Minecraft minecraft = this.minecraft;
        if (minecraft == null || minecraft.level == null) {
            return warnings;
        }

        if (!minecraft.hasSingleplayerServer()) {
            warnings.add(new WarningLine(Component.translatable("gui.adrenaline.warning.not_local_world"), 16755200));
            return warnings;
        }

        if (minecraft.getSingleplayerServer() != null && minecraft.getSingleplayerServer().isPublished()) {
            warnings.add(new WarningLine(Component.translatable("gui.adrenaline.warning.lan_world"), 16755200));
        }

        warnings.add(new WarningLine(Component.translatable("gui.adrenaline.warning.restart"), 11184810));
        return warnings;
    }

    private final class ThreadCountSlider extends AbstractSliderButton {

        private final Component label;
        private final IntConsumer setter;
        private final TooltipData tooltip;

        private ThreadCountSlider(int x, int y, int width, int height, Component label, IntSupplier getter, IntConsumer setter, TooltipData tooltip) {
            super(x, y, width, height, Component.empty(), AdrenalineConfigScreen.this.toSliderValue(getter.getAsInt()));
            this.label = label;
            this.setter = setter;
            this.tooltip = tooltip;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = AdrenalineConfigScreen.this.snapSliderValue(this.value);
            Component displayValue = value == 0 ? Component.translatable("gui.adrenaline.value.auto") : Component.literal(Integer.toString(value));
            if (value > AdrenalineConfig.recommendedWorkerThreads()) {
                displayValue = displayValue.copy().append(Component.literal(" (Not recommended)"));
            }
            this.setMessage(Component.translatable("gui.adrenaline.slider.threads", this.label, displayValue));
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
                this.setMessage(Component.translatable("gui.adrenaline.slider.spawn_zone_radius.default"));
                return;
            }
            this.setMessage(value == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? Component.translatable("gui.adrenaline.slider.spawn_zone_radius.instant") : Component.translatable("gui.adrenaline.slider.spawn_zone_radius.value", value));
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

    private final class FeatureSafetyRadiusSlider extends AbstractSliderButton {

        private FeatureSafetyRadiusSlider(int x, int y, int width, int height, TooltipData tooltip) {
            super(x, y, width, height, Component.empty(), AdrenalineConfigScreen.this.toFeatureSafetyRadiusSliderValue(AdrenalineConfigScreen.this.config.featureSafetyRadius));
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int radius = AdrenalineConfigScreen.this.snapFeatureSafetyRadiusSliderValue(this.value);
            this.setMessage(Component.translatable("gui.adrenaline.slider.feature_safety_radius", radius));
        }

        @Override
        protected void applyValue() {
            int radius = AdrenalineConfigScreen.this.snapFeatureSafetyRadiusSliderValue(this.value);
            this.value = AdrenalineConfigScreen.this.toFeatureSafetyRadiusSliderValue(radius);
            AdrenalineConfigScreen.this.config.featureSafetyRadius = radius;
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

    private double toFeatureSafetyRadiusSliderValue(int featureSafetyRadius) {
        int radius = featureSafetyRadius == 0 ? AdrenalineConfig.resolvedFeatureSafetyRadius() : featureSafetyRadius;
        int clamped = Math.max(AdrenalineConfig.MIN_FEATURE_SAFETY_RADIUS, Math.min(radius, AdrenalineConfig.MAX_FEATURE_SAFETY_RADIUS));
        int span = AdrenalineConfig.MAX_FEATURE_SAFETY_RADIUS - AdrenalineConfig.MIN_FEATURE_SAFETY_RADIUS;
        return span <= 0 ? 0.0D : (double) (clamped - AdrenalineConfig.MIN_FEATURE_SAFETY_RADIUS) / (double) span;
    }

    private int fromFeatureSafetyRadiusSliderValue(double value) {
        int span = AdrenalineConfig.MAX_FEATURE_SAFETY_RADIUS - AdrenalineConfig.MIN_FEATURE_SAFETY_RADIUS;
        return Math.min(AdrenalineConfig.MIN_FEATURE_SAFETY_RADIUS + (int) Math.round(value * span), AdrenalineConfig.MAX_FEATURE_SAFETY_RADIUS);
    }

    private int snapFeatureSafetyRadiusSliderValue(double value) {
        return this.fromFeatureSafetyRadiusSliderValue(value);
    }

    private Component stageLabel(String stage, int color) {
        return Component.translatable("gui.adrenaline.parallelize").append(Component.translatable(this.stageKey(stage)).withStyle(style -> style.withColor(color)));
    }

    private String stageKey(String stage) {
        return "gui.adrenaline.chunk_status." + stage.toLowerCase(Locale.ROOT);
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
        NONE("tooltip.adrenaline.performance.none", 0xAAAAAA),
        LOW("tooltip.adrenaline.performance.low", 0x55FF55),
        MEDIUM("tooltip.adrenaline.performance.medium", 0xFFFF55),
        HIGH("tooltip.adrenaline.performance.high", 0xFF5555),
        EXTREME("tooltip.adrenaline.performance.extreme", 0xAA00AA);

        private final String key;
        private final int color;

        PerformanceImpact(String key, int color) {
            this.key = key;
            this.color = color;
        }

        private String key() {
            return this.key;
        }
    }

    private static final class MutableBoolean {

        private boolean value;

        private MutableBoolean(boolean value) {
            this.value = value;
        }
    }
}
