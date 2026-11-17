package net.fly.adrenaline.compatdata;

import net.fly.adrenaline.AdrenalinePlatform;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class ModernFixCompat {

    public static final String WORLDGEN_ALLOCATION = "mixin.perf.worldgen_allocation";
    public static final String OPTIMIZE_SURFACE_RULES = "mixin.perf.optimize_surface_rules";
    public static final String RELEASE_PROTOCHUNKS = "mixin.perf.release_protochunks";

    private static final String[] REQUIRED_DISABLED_OPTIONS = {
        WORLDGEN_ALLOCATION,
        OPTIMIZE_SURFACE_RULES,
        RELEASE_PROTOCHUNKS
    };

    private static final Map<String, OptionState> OPTION_STATE_CACHE = new HashMap<>();

    private ModernFixCompat() {
    }

    public static void configureEarly() {
        for (String option : REQUIRED_DISABLED_OPTIONS) {
            System.setProperty("modernfix.config." + option, "false");
        }

        try {
            Object config = activeConfig();
            if (config != null) {
                Map<?, ?> options = optionMap(config);
                for (String option : REQUIRED_DISABLED_OPTIONS) {
                    Object value = options.get(option);
                    if (value != null && enabledValue(value.getClass(), value)) {
                        disableOption(value);
                    }
                }
            }
            OPTION_STATE_CACHE.clear();
            GlobalCommon.LOGGER.info("Applied ModernFix compatibility overrides for this launch");
        } catch (ReflectiveOperationException exception) {
            GlobalCommon.LOGGER.warn("Failed to apply live ModernFix compatibility overrides; the startup check will verify them", exception);
        }
    }

    public static void registerOverrideOwners() {
        if (!isPresent()) {
            return;
        }
        try {
            Object config = activeConfig();
            if (config == null) {
                return;
            }
            Map<?, ?> options = optionMap(config);
            for (String name : REQUIRED_DISABLED_OPTIONS) {
                Object option = options.get(name);
                if (option == null || enabledValue(option.getClass(), option)) {
                    continue;
                }
                Collection<?> owners = (Collection<?>) option.getClass().getMethod("getDefiningMods").invoke(option);
                if (owners.contains("adrenaline")) {
                    continue;
                }
                Set<String> updatedOwners = new LinkedHashSet<>();
                for (Object owner : owners) {
                    updatedOwners.add((String) owner);
                }
                updatedOwners.add("adrenaline");
                Field definingMods = option.getClass().getDeclaredField("modDefined");
                definingMods.setAccessible(true);
                definingMods.set(option, updatedOwners);
            }
        } catch (ReflectiveOperationException exception) {
            GlobalCommon.LOGGER.warn("Failed to register Adrenaline's ModernFix override ownership", exception);
        }
    }

    private static void disableOption(Object option) throws ReflectiveOperationException {
        Method override;
        try {
            override = option.getClass().getMethod("addModOverride", boolean.class, String.class);
        } catch (NoSuchMethodException exception) {
            override = option.getClass().getMethod("addModOverride", Object.class, String.class);
        }
        override.invoke(option, false, "adrenaline");
    }

    private static Object activeConfig() throws ReflectiveOperationException {
        Class<?> pluginClass = Class.forName("org.embeddedt.modernfix.core.ModernFixMixinPlugin");
        Object plugin = pluginClass.getField("instance").get(null);
        return plugin == null ? null : pluginClass.getField("config").get(plugin);
    }

    private static Map<?, ?> optionMap(Object config) throws ReflectiveOperationException {
        return (Map<?, ?>) config.getClass().getMethod("getOptionMap").invoke(config);
    }

    public static boolean isPresent() {
        return AdrenalinePlatform.isModLoaded("modernfix");
    }

    public static boolean needsConfigFix() {
        if (!isPresent()) {
            return false;
        }

        logDetectedState();
        for (String option : REQUIRED_DISABLED_OPTIONS) {
            OptionState state = optionState(option);
            if (!state.present()) {
                continue;
            }
            if (state.enabled()) {
                if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
                    GlobalCommon.LOGGER.info("ModernFix compatibility fix required: {} is enabled", option);
                }
                return true;
            }
        }
        if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
            GlobalCommon.LOGGER.info("ModernFix compatibility check passed");
        }
        return false;
    }

    public static boolean applyConfigFix() {
        Path configPath = configPath();
        Properties properties = readProperties();
        boolean changed = false;

        if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
            GlobalCommon.LOGGER.info("Applying ModernFix compatibility fix at {}", configPath);
            logDetectedState();
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
            if (BuildConfig.DEBUG && AdrenalineConfig.debugLoggingEnabled()) {
                GlobalCommon.LOGGER.info("Updated ModernFix mixin config at {}", configPath);
                logDetectedState();
            }
            return true;
        } catch (IOException exception) {
            GlobalCommon.LOGGER.warn("Failed to update ModernFix mixin config", exception);
            return false;
        }
    }

    private static Path configPath() {
        return AdrenalinePlatform.configDirectory().resolve("modernfix-mixins.properties");
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
            GlobalCommon.LOGGER.warn("Failed to read ModernFix mixin config", exception);
        }
        return properties;
    }

    public static boolean hasRequiredDisabledOptions() {
        if (!isPresent()) {
            return true;
        }

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
            Object config = activeConfig();
            if (config == null) {
                return OptionState.missing();
            }
            Object optionValue = optionMap(config).get(option);
            if (optionValue == null) {
                return OptionState.missing();
            }
            Class<?> optionClass = optionValue.getClass();
            boolean enabled = enabledValue(optionClass, optionValue);
            boolean userDefined = (boolean) optionClass.getMethod("isUserDefined").invoke(optionValue);
            return new OptionState(true, enabled, userDefined);
        } catch (ReflectiveOperationException exception) {
            GlobalCommon.LOGGER.warn("Failed to inspect ModernFix option state for {}", option, exception);
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

    private static void logDetectedState() {
        if (!BuildConfig.DEBUG || !AdrenalineConfig.debugLoggingEnabled()) {
            return;
        }

        GlobalCommon.LOGGER.info("ModernFix detected!");
        GlobalCommon.LOGGER.info("ModernFix config path: {}", configPath());
        for (String option : REQUIRED_DISABLED_OPTIONS) {
            OptionState state = optionState(option);
            if (!state.present()) {
                GlobalCommon.LOGGER.info("{}: Not present in installed ModernFix", option);
                continue;
            }
            if (!state.enabled() && !state.userDefined()) {
                GlobalCommon.LOGGER.info("{}: Disabled [DEFAULT]", option);
            } else if (!state.enabled()) {
                GlobalCommon.LOGGER.info("{}: Disabled [OK]", option);
            } else {
                GlobalCommon.LOGGER.info("{}: Enabled [RESTART BLOCKED]", option);
            }
        }
    }

    private record OptionState(boolean present, boolean enabled, boolean userDefined) {
        private static OptionState missing() {
            return new OptionState(false, false, false);
        }
    }
}
