package net.fly.adrenaline.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;

@Mixin(value = {NoiseBasedChunkGenerator.class, NoiseChunk.class, Aquifer.NoiseBasedAquifer.class, SurfaceSystem.class, Beardifier.class, ChunkMap.class, RandomState.class, NormalNoise.class, BlendedNoise.class, BiomeManager.class, Climate.ParameterList.class, PalettedContainer.class, ProtoChunk.class, Block.class, Blender.class, IOWorker.class, ChunkSerializer.class, ChunkTaskPriorityQueueSorter.class}, priority = 1)
public class MixinOptimizationTakeoverDetector {
}
