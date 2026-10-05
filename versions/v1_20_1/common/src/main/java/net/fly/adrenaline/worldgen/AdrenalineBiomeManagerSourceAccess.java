package net.fly.adrenaline.worldgen;

import net.minecraft.world.level.biome.BiomeManager;

public interface AdrenalineBiomeManagerSourceAccess {
    BiomeManager.NoiseBiomeSource adrenaline$getNoiseBiomeSource();

    long adrenaline$getBiomeZoomSeed();
}
