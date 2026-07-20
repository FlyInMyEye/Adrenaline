package net.fly.adrenaline.compatdata;

import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class ModernFixCompat {

    public static final String PAPER_CHUNK_PATCHES = "mixin.bugfix.paper_chunk_patches";
    public static final String CHUNK_DEADLOCK = "mixin.bugfix.chunk_deadlock";
    public static final String WORLDGEN_ALLOCATION = "mixin.perf.worldgen_allocation";
    public static final String REMOVE_SPAWN_CHUNKS = "mixin.perf.remove_spawn_chunks";
    public static final String OPTIMIZE_SURFACE_RULES = "mixin.perf.optimize_surface_rules";
    public static final String RELEASE_PROTOCHUNKS = "mixin.perf.release_protochunks";

    private static final String[] REQUIRED_DISABLED_OPTIONS = {
        PAPER_CHUNK_PATCHES,
        CHUNK_DEADLOCK,
        WORLDGEN_ALLOCATION,
        REMOVE_SPAWN_CHUNKS,
        OPTIMIZE_SURFACE_RULES,
        RELEASE_PROTOCHUNKS
    };

    private static final Map<String, OptionState> OPTION_STATE_CACHE = new HashMap<>();

    private ModernFixCompat() {
    }

    public static boolean isPresent() {
        return ModList.get().isLoaded("modernfix");
    }

    public static boolean needsConfigFix() {
        if (!isPresent()) {
            return false;
        }

        Properties properties = readProperties();
        logDetectedState(properties);
        for (String option : REQUIRED_DISABLED_OPTIONS) {
            OptionState state = optionState(option);
            if (!state.present()) {
                continue;
            }
            if (state.enabled()) {
                if (AdrenalineConfig.debugLoggingEnabled()) {
                    Adrenaline.LOGGER.info("ModernFix compatibility fix required: {} is enabled", option);
                }
                return true;
            }
        }
        if (AdrenalineConfig.debugLoggingEnabled()) {
            Adrenaline.LOGGER.info("ModernFix compatibility check passed");
        }
        return false;
    }

    public static boolean applyConfigFix() {
        Path configPath = configPath();
        Properties properties = readProperties();
        boolean changed = false;

        if (AdrenalineConfig.debugLoggingEnabled()) {
            Adrenaline.LOGGER.info("Applying ModernFix compatibility fix at {}", configPath);
            logDetectedState(properties);
        }

        for (String option : REQUIRED_DISABLED_OPTIONS) {
            OptionState state = optionState(option);
            if (!state.present()) {
                continue;
            }
            if (state.enabled()) {
                properties.setProperty(option, "false");
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
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                properties.store(writer, "ModernFix overrides added by Adrenaline");
            }
            OPTION_STATE_CACHE.clear();
            if (AdrenalineConfig.debugLoggingEnabled()) {
                Adrenaline.LOGGER.info("Updated ModernFix mixin config at {}", configPath);
                logDetectedState(properties);
            }
            return true;
        } catch (IOException exception) {
            Adrenaline.LOGGER.warn("Failed to update ModernFix mixin config", exception);
            return false;
        }
    }

    private static Path configPath() {
        return FMLPaths.CONFIGDIR.get().resolve("modernfix-mixins.properties");
    }

    private static Properties readProperties() {
        Properties properties = new Properties();
        Path configPath = configPath();
        if (!Files.exists(configPath)) {
            return properties;
        }

        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            Adrenaline.LOGGER.warn("Failed to read ModernFix mixin config", exception);
        }
        return properties;
    }

    public static boolean hasRequiredDisabledOptions() {
        if (!isPresent()) {
            return true;
        }

        Properties properties = readProperties();
        for (String option : REQUIRED_DISABLED_OPTIONS) {
            OptionState state = optionState(option);
            if (!state.present()) {
                continue;
            }
            if (state.enabled()) {
                return false;
            }
        }
        return true;
    }

    private static OptionState optionState(String option) {
        OptionState cached = OPTION_STATE_CACHE.get(option);
        if (cached != null) {
            return cached;
        }

        OptionState state = loadOptionState(option);
        OPTION_STATE_CACHE.put(option, state);
        return state;
    }

    private static OptionState loadOptionState(String option) {
        try {
            Class<?> configClass = Class.forName("org.embeddedt.modernfix.core.config.ModernFixEarlyConfig");
            Object earlyConfig = configClass.getMethod("load", File.class).invoke(null, configPath().toFile());
            @SuppressWarnings("unchecked")
            Map<String, Object> optionMap = (Map<String, Object>) configClass.getMethod("getOptionMap").invoke(earlyConfig);
            Object optionValue = optionMap.get(option);
            if (optionValue == null) {
                return OptionState.missing();
            }
            Class<?> optionClass = optionValue.getClass();
            boolean enabled = enabledValue(optionClass, optionValue);
            boolean userDefined = (boolean) optionClass.getMethod("isUserDefined").invoke(optionValue);
            return new OptionState(true, enabled, userDefined);
        } catch (ReflectiveOperationException exception) {
            Adrenaline.LOGGER.warn("Failed to inspect ModernFix option state for {}", option, exception);
            return OptionState.missing();
        }
    }

    private static boolean enabledValue(Class<?> optionClass, Object optionValue) throws ReflectiveOperationException {
        try {
            return (boolean) optionClass.getMethod("isEnabled").invoke(optionValue);
        } catch (NoSuchMethodException exception) {
            return (boolean) optionClass.getMethod("getValue").invoke(optionValue);
        }
    }

    private static void logDetectedState(Properties properties) {
        if (!AdrenalineConfig.debugLoggingEnabled()) {
            return;
        }

        Adrenaline.LOGGER.info("ModernFix detected!");
        Adrenaline.LOGGER.info("ModernFix config path: {}", configPath());
        for (String option : REQUIRED_DISABLED_OPTIONS) {
            OptionState state = optionState(option);
            if (!state.present()) {
                Adrenaline.LOGGER.info("{}: Not present in installed ModernFix", option);
                continue;
            }
            if (!state.enabled() && !state.userDefined()) {
                Adrenaline.LOGGER.info("{}: Disabled [DEFAULT]", option);
            } else if (!state.enabled()) {
                Adrenaline.LOGGER.info("{}: Disabled [OK]", option);
            } else {
                Adrenaline.LOGGER.info("{}: Enabled [RESTART BLOCKED]", option);
            }
        }
    }

    private record OptionState(boolean present, boolean enabled, boolean userDefined) {
        private static OptionState missing() {
            return new OptionState(false, false, false);
        }
    }
}
