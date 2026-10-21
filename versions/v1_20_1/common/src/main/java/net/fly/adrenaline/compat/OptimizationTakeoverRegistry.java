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
        TERRAIN_FILL, SECTION_TERRAIN, NOISE_SLICES, CELL_CACHE, INTERPOLATION, CELL_INTERPOLATION,
        PRELIMINARY_SURFACE, DENSITY_MAPPING, DENSITY_WRAPPERS, DENSITY_SPLINES,
        BIOME_CLIMATE, BIOME_FIDDLE,
        SURFACE_RULES, SURFACE_CONTEXT, SURFACE_COLUMN, SURFACE_SPANS, SURFACE_BIOMES,
        SURFACE_DEPTH_NOISE, SURFACE_BAND_NOISE,
        NATIVE_PERLIN, NATIVE_BLENDED_NOISE, NATIVE_SURFACE_RULES,
        AQUIFER, BEARDIFIER, ORE_VEIN, MATERIAL_RULE,
        INITIAL_SPAWN, SPAWN_RADIUS, SPAWN_WAIT, TERRAIN_LOADING,
        BACKGROUND_SAVE, GENERATION_DISPATCH, INLINE_GENERATION_TASKS,
        REGION_SCANS, BIOME_CODEC, CHUNK_SERIALIZATION, PALETTE_PACKING,
        HEIGHTMAP, SHAPE_CACHE, FLAT_CACHE, BLENDING, FAST_LEGACY_RANDOM
    }
}
