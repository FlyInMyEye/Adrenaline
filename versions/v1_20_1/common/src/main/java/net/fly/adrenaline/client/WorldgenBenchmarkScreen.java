package net.fly.adrenaline.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class WorldgenBenchmarkScreen extends Screen {

    private static final ResourceLocation PANEL_TEXTURE = new ResourceLocation("textures/gui/options_background.png");

    private final Screen parent;
    private Button actionButton;
    private Button differencesButton;
    private Button enterWorldButton;

    public WorldgenBenchmarkScreen(Screen parent) {
        super(Component.translatable("gui.adrenaline.benchmark.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.actionButton = this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> {
            if (WorldgenBenchmark.isRunning()) {
                WorldgenBenchmark.cancel();
            } else {
                this.minecraft.setScreen(this.parent);
            }
        }).bounds(this.width / 2 - 50, this.height - 32, 100, 20).build());
        this.differencesButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.benchmark.see_differences"), button -> {
            WorldgenBenchmark.Report report = WorldgenBenchmark.status().report();
            if (report != null) {
                this.minecraft.setScreen(new WorldgenDifferencesScreen(this, report));
            }
        }).bounds(this.width / 2 - 104, this.height - 32, 96, 20).build());
        this.enterWorldButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.benchmark.enter_world"), button -> WorldgenBenchmark.enterDifferenceWorld())
            .bounds(this.width / 2 + 4, this.height - 32, 96, 20).build());
        this.updateButton();
    }

    @Override
    public void tick() {
        this.updateButton();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.renderTiledPanel(guiGraphics, 0, 0, this.width, this.height);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 24, 16777215);
        int y = 54;
        for (FormattedLine line : this.lines()) {
            guiGraphics.drawCenteredString(this.font, line.text(), this.width / 2, y, line.color());
            y += 13;
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (!WorldgenBenchmark.isRunning()) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !WorldgenBenchmark.isRunning();
    }

    private void updateButton() {
        if (this.actionButton == null) {
            return;
        }
        this.actionButton.setMessage(WorldgenBenchmark.isRunning() ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_DONE);
        WorldgenBenchmark.Report report = WorldgenBenchmark.status().report();
        boolean showDifferences = !WorldgenBenchmark.isRunning() && report != null
            && (!report.noiseDifferences().isEmpty() || !report.fullDifferences().isEmpty());
        boolean showEnterWorld = !WorldgenBenchmark.isRunning() && report != null && !report.fullDifferences().isEmpty();
        this.differencesButton.visible = showDifferences;
        this.enterWorldButton.visible = showEnterWorld;
        if (showDifferences && showEnterWorld) {
            this.differencesButton.setX(this.width / 2 - 148);
            this.enterWorldButton.setX(this.width / 2 - 48);
            this.actionButton.setX(this.width / 2 + 52);
            this.actionButton.setWidth(96);
        } else if (showDifferences) {
            this.differencesButton.setX(this.width / 2 - 102);
            this.actionButton.setX(this.width / 2 + 2);
            this.actionButton.setWidth(100);
        } else {
            this.actionButton.setX(this.width / 2 - 50);
            this.actionButton.setWidth(100);
        }
    }

    private List<FormattedLine> lines() {
        WorldgenBenchmark.Status status = WorldgenBenchmark.status();
        List<FormattedLine> lines = new ArrayList<>();
        if (status.phase() == WorldgenBenchmark.Phase.COMPLETE && status.report() != null) {
            WorldgenBenchmark.Report report = status.report();
            double noiseBlockMatch = report.totalBlocks() == 0 ? 0.0D : (double) report.noiseMatchingBlocks() * 100.0D / (double) report.totalBlocks();
            double noiseChunkMatch = report.chunks() == 0 ? 0.0D : (double) report.noiseExactChunks() * 100.0D / (double) report.chunks();
            double fullBlockMatch = report.totalBlocks() == 0 ? 0.0D : (double) report.fullMatchingBlocks() * 100.0D / (double) report.totalBlocks();
            double fullChunkMatch = report.chunks() == 0 ? 0.0D : (double) report.fullExactChunks() * 100.0D / (double) report.chunks();
            long vanillaPreparationNanos = Math.max(0L, report.vanillaTotalNanos() - report.vanillaGenerationNanos());
            long adrenalinePreparationNanos = Math.max(0L, report.adrenalineTotalNanos() - report.adrenalineGenerationNanos());
            double generationSpeedup = report.generationSpeedPercent() - 100.0D;
            double totalSpeedup = report.totalSpeedPercent() - 100.0D;
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.seed", report.seed()), 0xAAAAAA));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.spawn_zone", report.spawnZoneRadius()), 0xAAAAAA));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.sample", report.chunks(), report.totalBlocks()), 0xAAAAAA));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.noise_block_match", format(noiseBlockMatch)), noiseBlockMatch == 100.0D ? 0x55FF55 : 0xFFFF55));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.noise_chunk_match", format(noiseChunkMatch), report.noiseExactChunks(), report.chunks()), noiseChunkMatch == 100.0D ? 0x55FF55 : 0xFFFF55));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.full_block_match", format(fullBlockMatch)), fullBlockMatch == 100.0D ? 0x55FF55 : 0xFFFF55));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.full_chunk_match", format(fullChunkMatch), report.fullExactChunks(), report.chunks()), fullChunkMatch == 100.0D ? 0x55FF55 : 0xFFFF55));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.vanilla_times", formatMillis(vanillaPreparationNanos), formatMillis(report.vanillaGenerationNanos()), formatMillis(report.vanillaTotalNanos())), 0xE0E0E0));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.adrenaline_times", formatMillis(adrenalinePreparationNanos), formatMillis(report.adrenalineGenerationNanos()), formatMillis(report.adrenalineTotalNanos())), 0xE0E0E0));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.generation_speed", format(report.generationSpeedPercent()), format(generationSpeedup)), generationSpeedup >= 0.0D ? 0x55FF55 : 0xFF5555));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.total_speed", format(report.totalSpeedPercent()), format(totalSpeedup)), totalSpeedup >= 0.0D ? 0x55FF55 : 0xFF5555));
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.report", report.differenceReport()), 0xAAAAAA));
            return lines;
        }
        if (status.phase() == WorldgenBenchmark.Phase.FAILED) {
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.failed"), 0xFF5555));
            lines.add(new FormattedLine(Component.literal(status.error() == null ? "Unknown failure" : status.error()), 0xFFAAAA));
            return lines;
        }
        if (status.phase() == WorldgenBenchmark.Phase.CANCELLED) {
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.cancelled"), 0xFFFF55));
            return lines;
        }

        Component passName = Component.translatable(WorldgenBenchmark.isAdrenalinePass() ? "gui.adrenaline.benchmark.adrenaline" : "gui.adrenaline.benchmark.vanilla");
        String phaseKey = switch (status.phase()) {
            case STARTING_VANILLA, STARTING_ADRENALINE -> "gui.adrenaline.benchmark.starting";
            case WARMING -> "gui.adrenaline.benchmark.warming";
            case GENERATING -> "gui.adrenaline.benchmark.generating";
            case COMPARING_NOISE -> "gui.adrenaline.benchmark.comparing_noise";
            case COMPARING_FULL -> "gui.adrenaline.benchmark.comparing_full";
            case CLEANING -> "gui.adrenaline.benchmark.cleaning";
            case CANCELLING -> "gui.adrenaline.benchmark.cancelling";
            default -> "gui.adrenaline.benchmark.starting";
        };
        lines.add(new FormattedLine(Component.translatable(phaseKey, passName), 0xFFFF55));
        if (status.totalChunks() > 0) {
            lines.add(new FormattedLine(Component.translatable("gui.adrenaline.benchmark.progress", status.completedChunks(), status.totalChunks()), 0xE0E0E0));
        }
        return lines;
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
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
