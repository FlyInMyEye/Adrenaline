# Setup

## Matrix

- Forge 1.20.1: Existing Adrenaline implementation
- Fabric 1.20.1: Active implementation
- Fabric 1.21.1: Scaffold target
- Forge 1.21.1: Scaffold target
- NeoForge 1.21.1: Scaffold target

## Suggested workflow

1. Put cross-version logic in `common/`.
2. Put version-sensitive shared logic in `versions/<target>/common/`.
3. Keep loader-specific integrations in each loader module.
4. Run `./gradlew build` from the repository root to verify the full matrix.
