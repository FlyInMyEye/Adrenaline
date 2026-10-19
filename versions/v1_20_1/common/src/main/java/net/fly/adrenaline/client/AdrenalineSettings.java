package net.fly.adrenaline.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.config.AdrenalineConfig.Data;
import net.fly.adrenaline.natives.AdrenalineNatives;
import net.minecraft.network.chat.Component;

final class AdrenalineSettings {
    static final Data DEFAULTS = new Data();
    static final List<Setting> ALL = create();

    private AdrenalineSettings() {
    }

    private static List<Setting> create() {
        List<Setting> settings = new ArrayList<>();
        settings.add(integer("generationWorkerThreads", "generation_threads", Group.SCHEDULING, true, Impact.HIGH, 0, Runtime.getRuntime().availableProcessors(), d -> d.generationWorkerThreads, (d, v) -> d.generationWorkerThreads = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("adaptiveGenerationWorkers", "adaptive_generation_workers", Group.SCHEDULING, true, Impact.VARIABLE, d -> d.adaptiveGenerationWorkers, (d, v) -> d.adaptiveGenerationWorkers = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelWorldgen", "parallel_worldgen", Group.SCHEDULING, true, Impact.EXTREME, d -> d.parallelWorldgen, (d, v) -> d.parallelWorldgen = v).controlled(Optimization.PARALLEL_WORLDGEN));
        settings.add(choice("stagePriority", "stage_priority", Group.SCHEDULING, false, Impact.VARIABLE, List.of(AdrenalineConfig.StagePriority.HIGHEST, AdrenalineConfig.StagePriority.FIFO, AdrenalineConfig.StagePriority.NEAREST), Data::stagePriority, Data::setStagePriority).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(integer("featureSafetyRadius", "feature_safety_radius", Group.SCHEDULING, false, Impact.VARIABLE, 1, 8, d -> d.featureSafetyRadius == 0 ? switch (d.featureCompatibility) { case "increased" -> 3; case "max" -> 7; default -> 1; } : d.featureSafetyRadius, (d, v) -> d.featureSafetyRadius = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("terrainFillOptimizations", "terrain_fill_optimizations", Group.TERRAIN, false, Impact.HIGH, d -> d.terrainFillOptimizations, (d, v) -> d.terrainFillOptimizations = v).controlled(Optimization.TERRAIN_FILL));
        settings.add(toggle("sectionTerrainOptimizations", "section_terrain_optimizations", Group.TERRAIN, false, Impact.HIGH, d -> d.sectionTerrainOptimizations, (d, v) -> d.sectionTerrainOptimizations = v).nativeBackend()
            .requires(d -> d.terrainFillOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.TERRAIN_FILL), "terrain_fill_optimizations")
            .requires(d -> d.noiseChunkOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.NOISE_CHUNK), "noise_chunk_optimizations")
            .requires(d -> d.aquiferOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.AQUIFER), "aquifer_optimizations")
            .requires(d -> d.nativeAquiferBatching, "native_aquifer_batching"));
        settings.add(toggle("noiseChunkOptimizations", "noise_chunk_optimizations", Group.TERRAIN, false, Impact.HIGH, d -> d.noiseChunkOptimizations, (d, v) -> d.noiseChunkOptimizations = v).controlled(Optimization.NOISE_CHUNK));
        settings.add(toggle("densityMappingOptimizations", "density_mapping_optimizations", Group.TERRAIN, false, Impact.MEDIUM, d -> d.densityMappingOptimizations, (d, v) -> d.densityMappingOptimizations = v).controlled(Optimization.NOISE_CHUNK));
        settings.add(toggle("materialRuleOptimizations", "material_rule_optimizations", Group.TERRAIN, false, Impact.LOW, d -> d.materialRuleOptimizations, (d, v) -> d.materialRuleOptimizations = v).controlled(Optimization.MATERIAL_RULE));
        settings.add(toggle("aquiferOptimizations", "aquifer_optimizations", Group.TERRAIN, false, Impact.MEDIUM, d -> d.aquiferOptimizations, (d, v) -> d.aquiferOptimizations = v).controlled(Optimization.AQUIFER));
        settings.add(toggle("beardifierOptimizations", "beardifier_optimizations", Group.TERRAIN, false, Impact.LOW, d -> d.beardifierOptimizations, (d, v) -> d.beardifierOptimizations = v).controlled(Optimization.BEARDIFIER));
        settings.add(toggle("oreVeinOptimizations", "ore_vein_optimizations", Group.TERRAIN, false, Impact.LOW, d -> d.oreVeinOptimizations, (d, v) -> d.oreVeinOptimizations = v).controlled(Optimization.ORE_VEIN));
        settings.add(toggle("blendingOptimizations", "blending_optimizations", Group.TERRAIN, false, Impact.LOW, d -> d.blendingOptimizations, (d, v) -> d.blendingOptimizations = v));
        settings.add(toggle("heightmapOptimizations", "heightmap_optimizations", Group.TERRAIN, false, Impact.LOW, d -> d.heightmapOptimizations, (d, v) -> d.heightmapOptimizations = v));
        settings.add(toggle("fastLegacyRandom", "fast_legacy_random", Group.TERRAIN, false, Impact.LOW, d -> d.fastLegacyRandom, (d, v) -> d.fastLegacyRandom = v).controlled(Optimization.FAST_LEGACY_RANDOM));
        settings.add(toggle("shapeCacheOptimizations", "shape_cache_optimizations", Group.TERRAIN, false, Impact.LOW, d -> d.shapeCacheOptimizations, (d, v) -> d.shapeCacheOptimizations = v));
        settings.add(toggle("flatCacheOptimizations", "flat_cache_optimizations", Group.BIOMES, false, Impact.MEDIUM, d -> d.flatCacheOptimizations, (d, v) -> d.flatCacheOptimizations = v).requires(d -> d.blendingOptimizations, "blending_optimizations"));
        settings.add(toggle("biomeSearchOptimizations", "biome_search_optimizations", Group.BIOMES, false, Impact.HIGH, d -> d.biomeSearchOptimizations, (d, v) -> d.biomeSearchOptimizations = v));
        settings.add(toggle("biomeClimateOptimizations", "biome_climate_optimizations", Group.BIOMES, false, Impact.MEDIUM, d -> d.biomeClimateOptimizations, (d, v) -> d.biomeClimateOptimizations = v).controlled(Optimization.NOISE_CHUNK));
        settings.add(toggle("biomeFiddleOptimizations", "biome_fiddle_optimizations", Group.BIOMES, false, Impact.MEDIUM, d -> d.biomeFiddleOptimizations, (d, v) -> d.biomeFiddleOptimizations = v).controlled(Optimization.BIOME_FIDDLE));
        settings.add(toggle("surfaceOptimizations", "surface_optimizations", Group.SURFACE, false, Impact.HIGH, d -> d.surfaceOptimizations, (d, v) -> d.surfaceOptimizations = v).controlled(Optimization.SURFACE));
        settings.add(toggle("surfaceNoiseOptimizations", "surface_noise_optimizations", Group.SURFACE, false, Impact.MEDIUM, d -> d.surfaceNoiseOptimizations, (d, v) -> d.surfaceNoiseOptimizations = v).controlled(Optimization.SURFACE_NOISE));
        settings.add(toggle("surfaceColumnOptimizations", "surface_column_optimizations", Group.SURFACE, false, Impact.MEDIUM, d -> d.surfaceColumnOptimizations, (d, v) -> d.surfaceColumnOptimizations = v).requires(d -> d.surfaceOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.SURFACE), "surface_optimizations"));
        settings.add(toggle("nativeSurfaceRules", "native_surface_rules", Group.SURFACE, false, Impact.HIGH, d -> d.nativeSurfaceRules, (d, v) -> d.nativeSurfaceRules = v).nativeBackend().requires(d -> d.surfaceOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.SURFACE), "surface_optimizations"));
        settings.add(toggle("nativePerlinBatching", "native_perlin_batching", Group.NATIVE, false, Impact.HIGH, d -> d.nativePerlinBatching, (d, v) -> d.nativePerlinBatching = v).nativeBackend());
        settings.add(toggle("nativeDensityEvaluation", "native_density_evaluation", Group.NATIVE, false, Impact.HIGH, d -> d.nativeDensityEvaluation, (d, v) -> d.nativeDensityEvaluation = v).nativeBackend().requires(d -> d.noiseChunkOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.NOISE_CHUNK), "noise_chunk_optimizations"));
        settings.add(toggle("nativeAquiferBatching", "native_aquifer_batching", Group.NATIVE, false, Impact.MEDIUM, d -> d.nativeAquiferBatching, (d, v) -> d.nativeAquiferBatching = v).nativeBackend().requires(d -> d.aquiferOptimizations && !OptimizationTakeoverRegistry.isControlled(Optimization.AQUIFER), "aquifer_optimizations"));
        settings.add(toggle("approximateNativePerlin", "approximate_native_perlin", Group.NATIVE, false, Impact.HIGH, d -> d.approximateNativePerlin, (d, v) -> d.approximateNativePerlin = v).nativeBackend().requires(d -> d.nativePerlinBatching, "native_perlin_batching"));
        settings.add(toggle("parallelChunkSerialization", "parallel_chunk_serialization", Group.SAVING, false, Impact.MEDIUM, d -> d.parallelChunkSerialization, (d, v) -> d.parallelChunkSerialization = v));
        settings.add(integer("serializationWorkerThreads", "serialization_threads", Group.SAVING, false, Impact.MEDIUM, 0, Runtime.getRuntime().availableProcessors(), d -> d.serializationWorkerThreads, (d, v) -> d.serializationWorkerThreads = v).requires(d -> d.parallelChunkSerialization, "parallel_chunk_serialization"));
        settings.add(toggle("regionScanOptimizations", "region_scan_optimizations", Group.SAVING, false, Impact.MEDIUM, d -> d.regionScanOptimizations, (d, v) -> d.regionScanOptimizations = v));
        settings.add(toggle("biomeCodecCache", "biome_codec_cache", Group.SAVING, false, Impact.LOW, d -> d.biomeCodecCache, (d, v) -> d.biomeCodecCache = v));
        settings.add(toggle("palettePackingOptimizations", "palette_packing_optimizations", Group.SAVING, false, Impact.MEDIUM, d -> d.palettePackingOptimizations, (d, v) -> d.palettePackingOptimizations = v));
        settings.add(toggle("saveChunksAfterWorldCreation", "save_chunks_after_world_creation", Group.SAVING, false, Impact.VARIABLE, d -> d.saveChunksAfterWorldCreation, (d, v) -> d.saveChunksAfterWorldCreation = v));
        settings.add(choice("incrementalSaveInterval", "incremental_save_interval", Group.SAVING, false, Impact.VARIABLE, List.of(0, 32, 64, 128, 256, 512), d -> d.incrementalSaveInterval, (d, v) -> d.incrementalSaveInterval = v));
        settings.add(toggle("skipSavingScreenAfterExit", "skip_saving_screen_after_exit", Group.SAVING, true, Impact.NONE, d -> d.skipSavingScreenAfterExit, (d, v) -> d.skipSavingScreenAfterExit = v).controlled(Optimization.BACKGROUND_SAVE));
        settings.add(integer("spawnZoneRadius", "spawn_zone_radius", Group.STARTUP, true, Impact.HIGH, 1, 30, d -> d.spawnZoneRadius, (d, v) -> d.spawnZoneRadius = v).controlled(Optimization.SPAWN_ZONE));
        settings.add(choice("warmupOnStartup", "warmup_on_startup", Group.STARTUP, true, Impact.VARIABLE, List.of(AdrenalineConfig.WarmupMode.ON, AdrenalineConfig.WarmupMode.NON_BLOCK, AdrenalineConfig.WarmupMode.OFF), Data::warmupMode, Data::setWarmupMode));
        settings.add(choice("fastTerrainLoadingMode", "fast_terrain_loading", Group.STARTUP, true, Impact.HIGH, List.of(AdrenalineConfig.FastTerrainLoadingMode.OFF, AdrenalineConfig.FastTerrainLoadingMode.ON, AdrenalineConfig.FastTerrainLoadingMode.EXTREME), Data::fastTerrainLoadingMode, Data::setFastTerrainLoadingMode));
        settings.add(toggle("initialSpawnOptimization", "initial_spawn_optimization", Group.STARTUP, false, Impact.HIGH, d -> d.initialSpawnOptimization, (d, v) -> d.initialSpawnOptimization = v).controlled(Optimization.INITIAL_SPAWN));
        settings.add(toggle("prepareWorldCreationContext", "prepare_world_creation_context", Group.STARTUP, false, Impact.HIGH, d -> d.prepareWorldCreationContext, (d, v) -> d.prepareWorldCreationContext = v));
        settings.add(toggle("showCancelButton", "show_cancel_button", Group.STARTUP, true, Impact.NONE, d -> d.showCancelButton, (d, v) -> d.showCancelButton = v));
        settings.add(toggle("showChunkPreview", "show_chunk_preview", Group.STARTUP, true, Impact.LOW, d -> d.showChunkPreview, (d, v) -> d.showChunkPreview = v));
        settings.add(choice("startBeforehand", "start_beforehand", Group.STARTUP, false, Impact.NONE, List.of(AdrenalineConfig.StartBeforehandMode.OFF, AdrenalineConfig.StartBeforehandMode.BORING, AdrenalineConfig.StartBeforehandMode.ON), d -> d.startBeforehand, (d, v) -> d.startBeforehand = v));
        settings.add(toggle("parallelizeStructureStarts", "parallelize_structure_starts", Group.STAGES, false, Impact.MEDIUM, d -> d.parallelizeStructureStarts, (d, v) -> d.parallelizeStructureStarts = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeStructureReferences", "parallelize_structure_references", Group.STAGES, false, Impact.LOW, d -> d.parallelizeStructureReferences, (d, v) -> d.parallelizeStructureReferences = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeBiomes", "parallelize_biomes", Group.STAGES, false, Impact.MEDIUM, d -> d.parallelizeBiomes, (d, v) -> d.parallelizeBiomes = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeNoise", "parallelize_noise", Group.STAGES, false, Impact.HIGH, d -> d.parallelizeNoise, (d, v) -> d.parallelizeNoise = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeSurface", "parallelize_surface", Group.STAGES, false, Impact.HIGH, d -> d.parallelizeSurface, (d, v) -> d.parallelizeSurface = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeCarvers", "parallelize_carvers", Group.STAGES, false, Impact.LOW, d -> d.parallelizeCarvers, (d, v) -> d.parallelizeCarvers = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeFeatures", "parallelize_features", Group.STAGES, false, Impact.HIGH, d -> d.parallelizeFeatures, (d, v) -> d.parallelizeFeatures = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeInitializeLight", "parallelize_initialize_light", Group.STAGES, false, Impact.MEDIUM, d -> d.parallelizeInitializeLight, (d, v) -> d.parallelizeInitializeLight = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeLight", "parallelize_light", Group.STAGES, false, Impact.MEDIUM, d -> d.parallelizeLight, (d, v) -> d.parallelizeLight = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeSpawn", "parallelize_spawn", Group.STAGES, false, Impact.LOW, d -> d.parallelizeSpawn, (d, v) -> d.parallelizeSpawn = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        settings.add(toggle("parallelizeFull", "parallelize_full", Group.STAGES, false, Impact.LOW, d -> d.parallelizeFull, (d, v) -> d.parallelizeFull = v).requires(d -> d.parallelWorldgen && !OptimizationTakeoverRegistry.isControlled(Optimization.PARALLEL_WORLDGEN), "parallel_worldgen"));
        if (BuildConfig.DEBUG) {
            settings.add(toggle("showThreadVisualizer", "show_thread_visualizer", Group.DIAGNOSTICS, false, Impact.LOW, d -> d.showThreadVisualizer, (d, v) -> d.showThreadVisualizer = v));
            settings.add(toggle("debugLogging", "debug_logging", Group.DIAGNOSTICS, false, Impact.LOW, d -> d.debugLogging, (d, v) -> d.debugLogging = v));
            settings.add(toggle("forceEasterEgg", "force_easter_egg", Group.DIAGNOSTICS, false, Impact.NONE, d -> d.forceEasterEgg, (d, v) -> d.forceEasterEgg = v));
        }
        return List.copyOf(settings);
    }

    private static Setting toggle(String id, String key, Group group, boolean main, Impact impact, Function<Data, Boolean> getter, BiConsumer<Data, Boolean> setter) {
        return choice(id, key, group, main, impact, List.of(false, true), getter, setter);
    }

    private static <T> Setting choice(String id, String key, Group group, boolean main, Impact impact, List<T> values, Function<Data, T> getter, BiConsumer<Data, T> setter) {
        return new Setting(id, key, group, main, impact, d -> label(key, getter.apply(d)),
            d -> setter.accept(d, values.get((values.indexOf(getter.apply(d)) + 1) % values.size())),
            (d, source) -> setter.accept(d, getter.apply(source)),
            (a, b) -> Objects.equals(getter.apply(a), getter.apply(b)), null, null, 0, 0);
    }

    private static Setting integer(String id, String key, Group group, boolean main, Impact impact, int min, int max, ToIntFunction<Data> getter, BiConsumer<Data, Integer> setter) {
        return new Setting(id, key, group, main, impact, d -> integerLabel(key, getter.applyAsInt(d)), null,
            (d, source) -> setter.accept(d, getter.applyAsInt(source)),
            (a, b) -> getter.applyAsInt(a) == getter.applyAsInt(b), getter, setter, min, max);
    }

    private static Component integerLabel(String key, int value) {
        if (key.endsWith("threads") && value == 0) {
            return Component.translatable("gui.adrenaline.value.auto").append(" (" + AdrenalineConfig.recommendedWorkerThreads() + ")");
        }
        if (key.equals("spawn_zone_radius")) {
            return Component.translatable("gui.adrenaline.value.spawn_area", value * 2 - 1, value * 2 - 1);
        }
        return Component.literal(Integer.toString(value));
    }

    private static Component label(String key, Object value) {
        if (value instanceof Boolean enabled) {
            return Component.translatable(enabled ? "gui.adrenaline.toggle.on" : "gui.adrenaline.toggle.off")
                .withStyle(style -> style.withColor(enabled ? 0x55FF55 : 0xFF5555));
        }
        if (value instanceof AdrenalineConfig.StagePriority priority) {
            return Component.translatable("gui.adrenaline.stage_priority." + priority.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(style -> style.withColor(switch (priority) { case FIFO -> 0xAAAAAA; case HIGHEST -> 0xFFFF55; case NEAREST -> 0x55FF55; }));
        }
        if (value instanceof Enum<?> mode) {
            String name = mode.name().toLowerCase(java.util.Locale.ROOT);
            if (key.equals("warmup_on_startup")) {
                name = switch (name) { case "on" -> "blocking"; case "non_block" -> "background"; default -> name; };
            }
            int color = switch (name) { case "off" -> 0xFF5555; case "on", "blocking" -> 0x55FF55; default -> 0xFFFF55; };
            return Component.translatable("gui.adrenaline.toggle." + name).withStyle(style -> style.withColor(color));
        }
        return value.equals(0) ? Component.translatable("gui.adrenaline.toggle.off").withStyle(style -> style.withColor(0xFF5555))
            : Component.literal(value.toString()).withStyle(style -> style.withColor(0xFFFF55));
    }

    enum Impact {
        NONE(0xAAAAAA), LOW(0x55FF55), MEDIUM(0xFFFF55), HIGH(0xFF5555), EXTREME(0xAA00AA), VARIABLE(0xFFAA00);

        private final int color;

        Impact(int color) {
            this.color = color;
        }

        Component label() {
            return Component.translatable("tooltip.adrenaline.performance." + this.name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(style -> style.withColor(this.color));
        }
    }

    enum Group {
        ALL, SCHEDULING, TERRAIN, BIOMES, SURFACE, NATIVE, SAVING, STARTUP, STAGES, DIAGNOSTICS;

        Component title() {
            return Component.translatable("gui.adrenaline.config.group." + this.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    private record Requirement(Function<Data, Boolean> test, String key) {
    }

    static final class Setting {
        final String id;
        final String key;
        final Group group;
        final boolean main;
        final Impact impact;
        final Function<Data, Component> label;
        final Consumer<Data> cycle;
        final BiConsumer<Data, Data> copy;
        final BiPredicate<Data, Data> equal;
        final ToIntFunction<Data> integer;
        final BiConsumer<Data, Integer> setInteger;
        final int min;
        final int max;
        private Optimization controlled;
        private boolean nativeBackend;
        private final List<Requirement> requirements = new ArrayList<>();

        private Setting(String id, String key, Group group, boolean main, Impact impact, Function<Data, Component> label,
                        Consumer<Data> cycle, BiConsumer<Data, Data> copy, BiPredicate<Data, Data> equal,
                        ToIntFunction<Data> integer, BiConsumer<Data, Integer> setInteger, int min, int max) {
            this.id = id;
            this.key = key;
            this.group = group;
            this.main = main;
            this.impact = impact;
            this.label = label;
            this.cycle = cycle;
            this.copy = copy;
            this.equal = equal;
            this.integer = integer;
            this.setInteger = setInteger;
            this.min = min;
            this.max = max;
        }

        private Setting controlled(Optimization optimization) {
            this.controlled = optimization;
            return this;
        }

        private Setting nativeBackend() {
            this.nativeBackend = true;
            return this;
        }

        private Setting requires(Function<Data, Boolean> requirement, String key) {
            this.requirements.add(new Requirement(requirement, key));
            return this;
        }

        Component title() {
            if (this.group == Group.STAGES) {
                String stage = this.key.substring("parallelize_".length());
                int color = switch (stage) {
                    case "structure_starts" -> 10066329;
                    case "structure_references" -> 6250897;
                    case "biomes" -> 8434258;
                    case "noise" -> 13750737;
                    case "surface" -> 7497737;
                    case "carvers" -> 3159410;
                    case "features" -> 2213376;
                    case "initialize_light" -> 13421772;
                    case "light" -> 16769184;
                    case "spawn" -> 15884384;
                    default -> 16777215;
                };
                return Component.translatable("gui.adrenaline.parallelize")
                    .append(Component.translatable("gui.adrenaline.chunk_status." + stage).withStyle(style -> style.withColor(color)));
            }
            return Component.translatable("gui.adrenaline.config." + this.key);
        }

        Component description() {
            return Component.translatable("tooltip.adrenaline.config." + this.key);
        }

        Component inactiveReason(Data draft) {
            if (this.controlled != null) {
                String controller = OptimizationTakeoverRegistry.controller(this.controlled).orElse(null);
                if (controller != null) {
                    return Component.translatable("tooltip.adrenaline.config.optimization_controlled", controller);
                }
            }
            if (this.nativeBackend && !AdrenalineNatives.isAvailable()) {
                return Component.translatable("gui.adrenaline.config.native_unavailable");
            }
            for (Requirement required : this.requirements) {
                if (!required.test.apply(draft)) {
                    return Component.translatable("gui.adrenaline.config.requires", Component.translatable("gui.adrenaline.config." + required.key));
                }
            }
            if (this.key.equals("surface_noise_optimizations") && draft.nativePerlinBatching && draft.approximateNativePerlin && AdrenalineNatives.isAvailable()) {
                return Component.translatable("gui.adrenaline.config.requires_exact_noise");
            }
            return null;
        }
    }
}
