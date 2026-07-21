# Adrenaline

<p align="center">
  <img src="https://raw.githubusercontent.com/FlyInMyEye/Adrenaline/master/src/main/resources/adrenaline.png" alt="Adrenaline" width="300">
</p>

<p align="center">
  <a href="https://github.com/FlyInMyEye/Adrenaline"><img src="https://img.shields.io/github/stars/FlyInMyEye/Adrenaline?style=flat&label=GitHub" alt="GitHub"></a>
  <a href="https://modrinth.com/mod/flys-adrenaline"><img src="https://img.shields.io/modrinth/dt/flys-adrenaline?logo=modrinth&label=Modrinth&style=flat&color=242629&labelColor=5CA424&logoColor=1C1C1C" alt="Modrinth"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/flys-adrenaline"><img src="https://img.shields.io/badge/CurseForge-page-F16436?style=flat&logo=curseforge&logoColor=1C1C1C&labelColor=F16436&color=242629" alt="CurseForge"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg?style=flat" alt="License"></a>
</p>

<p align="center"><i>Generation multithreading, but better.</i></p>

Adrenaline is a Forge mod for Minecraft 1.20.1 focused on world generation performance. It adds parallel chunk generation, configurable worldgen optimizations, and compatibility handling for problematic mod combinations.

<p align="center">
  <img src="https://cdn.modrinth.com/data/cached_images/de76a6583c0ad529c4838a64d236679e2d8c10ac.gif" alt="Vanilla generation (slow)" width="300">
  <img src="https://cdn.modrinth.com/data/cached_images/ea86d1ec66b0d4275953a620d52006488bdc30af.gif" alt="With Adrenaline (fast)" width="300">
</p>

---

## Features

- Parallel chunk generation with configurable worker threads
- Worldgen optimization toggles for terrain fill, surface, noise, aquifers, beardifier, ore veins, and more
- Feature safety radius controls for safer feature placement with modded worldgen
- ModernFix and Fastload compatibility handling
- Worldgen statistics overlay and debug logging in dev builds
- Runtime config stored in `config/adrenaline.json`

---

## Installation

1. Install Forge for Minecraft 1.20.1
2. Download the Adrenaline jar from the project pages below
3. Place the jar in your `mods` folder
4. Launch the game

Project pages:

- [GitHub](https://github.com/FlyInMyEye/Adrenaline)
- [Modrinth](https://modrinth.com/mod/flys-adrenaline)
- [CurseForge](https://www.curseforge.com/minecraft/mc-mods/flys-adrenaline)

### Supported Versions

| Minecraft | Loader | Status |
|-----------|--------|--------|
| 1.20.1 | Forge 47.x | Active |

---

## Configuration

Adrenaline creates `config/adrenaline.json` on first launch.

Settings:

- `worldgenOptimizations` - master switch for worldgen changes
- `terrainFillOptimizations` - speeds up terrain fill during noise generation
- `surfaceOptimizations` - speeds up surface rule evaluation and block placement
- `noiseChunkOptimizations` - optimizes hot paths in noise chunk sampling
- `materialRuleOptimizations` - reduces material rule dispatch overhead
- `aquiferOptimizations` - reduces aquifer lookup and fluid decision cost
- `beardifierOptimizations` - speeds up structure terrain blending
- `oreVeinOptimizations` - speeds up ore vein sampling
- `initialSpawnOptimization` - skips vanilla's slow spawn refinement pass
- `generationWorkerThreads` - number of chunk generation workers
- `serializationWorkerThreads` - number of chunk save workers
- `spawnZoneRadius` - spawn generation radius in chunks
- `parallelWorldgen` - enables parallel world generation
- `featureCompatibility` - compatibility mode for widened feature writes
- `featureSafetyRadius` - reserved radius for feature generation
- `parallelizeStructureStarts` - parallelize the STRUCTURE_STARTS stage
- `parallelizeStructureReferences` - parallelize the STRUCTURE_REFERENCES stage
- `parallelizeBiomes` - parallelize the BIOMES stage
- `parallelizeNoise` - parallelize the NOISE stage
- `parallelizeSurface` - parallelize the SURFACE stage
- `parallelizeCarvers` - parallelize the CARVERS stage
- `parallelizeFeatures` - parallelize the FEATURES stage
- `parallelizeInitializeLight` - parallelize the INITIALIZE_LIGHT stage
- `parallelizeLight` - parallelize the LIGHT stage
- `parallelizeSpawn` - parallelize the SPAWN stage
- `parallelizeFull` - parallelize the FULL stage
- `fastLegacyRandom` - use the faster legacy random implementation
- `debugLogging` - emit extra diagnostics to the console

---

## Building from Source

```sh
./gradlew build
```

Prod build:

```sh
./gradlew buildProd
```

Built jars are in `build/libs/`.

---
