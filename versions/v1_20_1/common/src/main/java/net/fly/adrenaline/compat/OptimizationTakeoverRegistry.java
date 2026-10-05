package net.fly.adrenaline.compat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.fly.adrenaline.GlobalCommon;

public final class OptimizationTakeoverRegistry {
    private static final Map<Optimization, String> CONTROLLERS = new EnumMap<>(Optimization.class);
    private static volatile long controlledOptimizations;

    private OptimizationTakeoverRegistry() {
    }

    public static synchronized void record(Optimization optimization, String controller) {
        if (CONTROLLERS.putIfAbsent(optimization, controller) == null) {
            controlledOptimizations |= 1L << optimization.ordinal();
            GlobalCommon.LOGGER.info("{} took control of the {} optimization", controller, optimization.name().toLowerCase());
        }
    }

    public static boolean isControlled(Optimization optimization) {
        return (controlledOptimizations & (1L << optimization.ordinal())) != 0L;
    }

    public static synchronized Optional<String> controller(Optimization optimization) {
        return Optional.ofNullable(CONTROLLERS.get(optimization));
    }

    public enum Optimization {
        TERRAIN_FILL,
        SURFACE,
        SURFACE_NOISE,
        NOISE_CHUNK,
        BIOME_FIDDLE,
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
