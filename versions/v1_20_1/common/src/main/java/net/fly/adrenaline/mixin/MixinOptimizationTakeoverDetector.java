package net.fly.adrenaline.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = {NoiseBasedChunkGenerator.class, NoiseChunk.class, Aquifer.NoiseBasedAquifer.class, SurfaceSystem.class, Beardifier.class, ChunkMap.class}, priority = 500)
public class MixinOptimizationTakeoverDetector {
}
