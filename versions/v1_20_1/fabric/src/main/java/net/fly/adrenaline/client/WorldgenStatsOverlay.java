package net.fly.adrenaline.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.fly.adrenaline.util.WorldgenStageStats.StageTiming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class WorldgenStatsOverlay {

    private static final int BACKGROUND_COLOR = -1873784752;
    private static final int TEXT_COLOR = 14737632;
    private static final int MAX_BARS = 50;

    private WorldgenStatsOverlay() {
    }

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
            ClientCommandManager.literal("adrenaline")
                .then(ClientCommandManager.literal("stats")
                    .then(ClientCommandManager.literal("on").executes(context -> setEnabled(context.getSource(), true)))
                    .then(ClientCommandManager.literal("off").executes(context -> setEnabled(context.getSource(), false))))
        ));
        HudRenderCallback.EVENT.register(WorldgenStatsOverlay::render);
    }

    private static void render(GuiGraphics graphics, float tickDelta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!WorldgenStageStats.isEnabled() || minecraft.options.renderDebug) {
            return;
        }

        Font font = minecraft.font;
        List<String> lines = lines();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            int x = 2;
            int y = 2 + font.lineHeight * index;
            int width = font.width(line);
            graphics.fill(x - 1, y - 1, x + width + 1, y + font.lineHeight - 1, BACKGROUND_COLOR);
            graphics.drawString(font, line, x, y, TEXT_COLOR, false);
        }
    }

    private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
        WorldgenStageStats.setEnabled(enabled);
        source.sendFeedback(Component.translatable(enabled ? "message.adrenaline.stats.enabled" : "message.adrenaline.stats.disabled"));
        return 1;
    }

    private static List<String> lines() {
        List<StageTiming> timings = WorldgenStageStats.snapshot();
        long maxNanos = 0L;
        for (StageTiming timing : timings) {
            if (!isWaitingTiming(timing.name())) {
                maxNanos = Math.max(maxNanos, timing.averageNanos());
            }
        }

        List<String> lines = new ArrayList<>(timings.size());
        for (StageTiming timing : timings) {
            double milliseconds = timing.averageNanos() / 1_000_000.0D;
            String name = displayName(timing.name());
            if (isWaitingTiming(timing.name())) {
                lines.add(String.format(Locale.ROOT, "%-20s %.3fms", name, milliseconds));
                continue;
            }
            int bars = timing.averageNanos() == 0L || maxNanos == 0L
                ? 0
                : Math.max(1, (int) Math.round((double) timing.averageNanos() * MAX_BARS / maxNanos));
            lines.add(String.format(Locale.ROOT, "%-20s %s %.3fms", name, "|".repeat(bars), milliseconds));
        }
        return lines;
    }

    private static boolean isWaitingTiming(String name) {
        return switch (name.trim()) {
            case "WAITING", "DEPENDENCY", "CAPACITY", "FOOTPRINT CONFLICT", "EXECUTOR QUEUE", "UNCLASSIFIED" -> true;
            default -> false;
        };
    }

    private static String displayName(String name) {
        String trimmedName = name.trim();
        String key = switch (trimmedName) {
            case "SCHEDULING" -> "gui.adrenaline.worldgen_stats.scheduling";
            case "WAITING" -> "gui.adrenaline.worldgen_stats.waiting";
            case "SETUP" -> "gui.adrenaline.worldgen_stats.setup";
            case "SLICE SAMPLING" -> "gui.adrenaline.worldgen_stats.slice_sampling";
            case "CELL CACHE" -> "gui.adrenaline.worldgen_stats.cell_cache";
            case "ORE CACHE" -> "gui.adrenaline.worldgen_stats.ore_cache";
            case "INTERPOLATION" -> "gui.adrenaline.worldgen_stats.interpolation";
            case "BLOCK STATE" -> "gui.adrenaline.worldgen_stats.block_state";
            case "BLOCK WRITE" -> "gui.adrenaline.worldgen_stats.block_write";
            case "FINALIZE" -> "gui.adrenaline.worldgen_stats.finalize";
            case "DEPENDENCY" -> "gui.adrenaline.worldgen_stats.dependency";
            case "CAPACITY" -> "gui.adrenaline.worldgen_stats.capacity";
            case "FOOTPRINT CONFLICT" -> "gui.adrenaline.worldgen_stats.footprint_conflict";
            case "EXECUTOR QUEUE" -> "gui.adrenaline.worldgen_stats.executor_queue";
            case "UNCLASSIFIED" -> "gui.adrenaline.worldgen_stats.unclassified";
            case "EMPTY" -> "gui.adrenaline.chunk_status.empty";
            case "STRUCTURE_STARTS" -> "gui.adrenaline.chunk_status.structure_starts";
            case "STRUCTURE_REFERENCES" -> "gui.adrenaline.chunk_status.structure_references";
            case "BIOMES" -> "gui.adrenaline.chunk_status.biomes";
            case "NOISE" -> "gui.adrenaline.chunk_status.noise";
            case "SURFACE" -> "gui.adrenaline.chunk_status.surface";
            case "CARVERS" -> "gui.adrenaline.chunk_status.carvers";
            case "FEATURES" -> "gui.adrenaline.chunk_status.features";
            case "INITIALIZE_LIGHT" -> "gui.adrenaline.chunk_status.initialize_light";
            case "LIGHT" -> "gui.adrenaline.chunk_status.light";
            case "SPAWN" -> "gui.adrenaline.chunk_status.spawn";
            case "FULL" -> "gui.adrenaline.chunk_status.full";
            default -> null;
        };
        if (key == null) {
            return name;
        }
        int indentation = name.length() - name.stripLeading().length();
        return " ".repeat(indentation) + Component.translatable(key).getString();
    }
}
