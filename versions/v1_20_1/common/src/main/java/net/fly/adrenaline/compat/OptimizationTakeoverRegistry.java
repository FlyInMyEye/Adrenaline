package net.fly.adrenaline.compat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.fly.adrenaline.GlobalCommon;

public final class OptimizationTakeoverRegistry {
    private static final Map<Optimization, String> CONTROLLERS = new EnumMap<>(Optimization.class);

    private OptimizationTakeoverRegistry() {
    }

    public static synchronized void record(Optimization optimization, String controller) {
        if (CONTROLLERS.putIfAbsent(optimization, controller) == null) {
            GlobalCommon.LOGGER.info("{} took control of the {} optimization", controller, optimization.name().toLowerCase());
        }
    }

    public static synchronized boolean isControlled(Optimization optimization) {
        return CONTROLLERS.containsKey(optimization);
    }

    public static synchronized Optional<String> controller(Optimization optimization) {
        return Optional.ofNullable(CONTROLLERS.get(optimization));
    }

    public enum Optimization {
        TERRAIN_FILL,
        SURFACE,
        NOISE_CHUNK,
        MATERIAL_RULE,
        AQUIFER,
        BEARDIFIER,
        ORE_VEIN,
        INITIAL_SPAWN,
        SPAWN_ZONE,
        BACKGROUND_SAVE,
        FAST_LEGACY_RANDOM,
        PARALLEL_WORLDGEN
    }
}
