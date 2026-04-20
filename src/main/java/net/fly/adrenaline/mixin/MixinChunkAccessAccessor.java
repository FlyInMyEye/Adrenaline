package net.fly.adrenaline.mixin;

import java.util.Map;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkAccess.class)
public interface MixinChunkAccessAccessor {

    @Accessor("sections")
    LevelChunkSection[] adrenaline$getSections();

    @Accessor("heightmaps")
    Map<Types, Heightmap> adrenaline$getHeightmaps();
}
