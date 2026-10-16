package net.fly.adrenaline.mixin.levelgen;

import java.util.function.Predicate;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$BiomeConditionSource")
public interface AdrenalineMixinSurfaceBiomeConditionSource {

    @Accessor("biomeNameTest")
    Predicate<ResourceKey<Biome>> adrenaline$biomeNameTest();
}
