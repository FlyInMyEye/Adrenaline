package net.fly.adrenaline.mixin;

import net.minecraft.util.BitStorage;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Heightmap.class)
public interface MixinHeightmapAccessor {

    @Accessor("data")
    BitStorage adrenaline$getData();

    @Invoker("setHeight")
    void adrenaline$setHeight(int x, int z, int height);
}
