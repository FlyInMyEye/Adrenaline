package net.fly.adrenaline.mixin;

import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LegacyRandomSource.class)
public interface MixinLegacyRandomSourceAccessor {

    @Accessor("seed")
    AtomicLong adrenaline$getSeed();
}
