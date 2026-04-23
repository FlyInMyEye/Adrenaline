package net.fly.adrenaline.compatdata;

import net.fly.adrenaline.Adrenaline;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ModernFixCompat {

    public static final String PAPER_CHUNK_PATCHES = "mixin.bugfix.paper_chunk_patches";
    public static final String CHUNK_DEADLOCK = "mixin.bugfix.chunk_deadlock";
    public static final String WORLDGEN_ALLOCATION = "mixin.perf.worldgen_allocation";
    public static final String OPTIMIZE_SURFACE_RULES = "mixin.perf.optimize_surface_rules";
    public static final String RELEASE_PROTOCHUNKS = "mixin.perf.release_protochunks";

    private static final String[] REQUIRED_DISABLED_OPTIONS = {
        PAPER_CHUNK_PATCHES,
        CHUNK_DEADLOCK,
        WORLDGEN_ALLOCATION,
        OPTIMIZE_SURFACE_RULES,
        RELEASE_PROTOCHUNKS
    };

    private ModernFixCompat() {
    }

    public static boolean isPresent() {
        return ModList.get().isLoaded("modernfix");
    }

    public static boolean needsConfigFix() {
        if (!isPresent()) {
            return false;
        }

        List<String> lines = readLines();
        for (String option : REQUIRED_DISABLED_OPTIONS) {
            if (!"false".equalsIgnoreCase(readValue(lines, option))) {
                return true;
            }
        }
        return false;
    }

    public static boolean applyConfigFix() {
        Path configPath = configPath();
        List<String> lines = readLines();
        boolean changed = false;

        for (String option : REQUIRED_DISABLED_OPTIONS) {
            if (setValue(lines, option, "false")) {
                changed = true;
            }
        }

        if (!changed) {
            return true;
        }

        try {
            Path parent = configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(configPath, lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            Adrenaline.LOGGER.warn("Failed to update ModernFix mixin config", exception);
            return false;
        }
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve("modernfix-mixins.properties");
    }

    private static List<String> readLines() {
        Path configPath = configPath();
        if (!Files.exists(configPath)) {
            return new ArrayList<>();
        }

        try {
            return new ArrayList<>(Files.readAllLines(configPath, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            Adrenaline.LOGGER.warn("Failed to read ModernFix mixin config", exception);
            return new ArrayList<>();
        }
    }

    private static String readValue(List<String> lines, String key) {
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || !trimmed.startsWith(key + "=")) {
                continue;
            }
            return trimmed.substring(key.length() + 1).trim();
        }
        return null;
    }

    private static boolean setValue(List<String> lines, String key, String value) {
        String target = key + "=" + value;
        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("#") || !trimmed.startsWith(key + "=")) {
                continue;
            }
            if (trimmed.equals(target)) {
                return false;
            }
            lines.set(i, target);
            return true;
        }

        if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
            lines.add("");
        }
        lines.add(target);
        return true;
    }
}
