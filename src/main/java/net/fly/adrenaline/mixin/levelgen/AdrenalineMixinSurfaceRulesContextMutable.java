package net.fly.adrenaline.mixin.levelgen;

import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context")
public interface AdrenalineMixinSurfaceRulesContextMutable {

    @Mutable
    @Accessor("chunk")
    void adrenaline$setChunk(ChunkAccess chunk);

    @Mutable
    @Accessor("noiseChunk")
    void adrenaline$setNoiseChunk(NoiseChunk noiseChunk);

    @Mutable
    @Accessor("biomeGetter")
    void adrenaline$setBiomeGetter(Function<BlockPos, Holder<Biome>> biomeGetter);

    @Accessor("lastPreliminarySurfaceCellOrigin")
    void adrenaline$setLastPreliminarySurfaceCellOrigin(long lastPreliminarySurfaceCellOrigin);

    @Accessor("lastUpdateXZ")
    void adrenaline$setLastUpdateXZ(long lastUpdateXZ);

    @Accessor("lastSurfaceDepth2Update")
    void adrenaline$setLastSurfaceDepth2Update(long lastSurfaceDepth2Update);

    @Accessor("lastMinSurfaceLevelUpdate")
    void adrenaline$setLastMinSurfaceLevelUpdate(long lastMinSurfaceLevelUpdate);

    @Accessor("lastUpdateY")
    void adrenaline$setLastUpdateY(long lastUpdateY);

    @Accessor("biome")
    void adrenaline$setBiome(Supplier<Holder<Biome>> biome);
}
