package net.fly.adrenaline.util;

import net.fly.adrenaline.mixin.MixinLegacyRandomSourceAccessor;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

public final class NonAtomicLegacyRandomSource extends LegacyRandomSource {

    private long plainSeed;

    public NonAtomicLegacyRandomSource(long seed) {
        super(seed);
        this.plainSeed = getRawSeed();
    }

    private long getRawSeed() {
        return ((MixinLegacyRandomSourceAccessor) (Object) this).adrenaline$getSeed().get();
    }

    @Override
    public void setSeed(long seed) {
        this.plainSeed = (seed ^ 25214903917L) & 281474976710655L;
    }

    @Override
    public int next(int bits) {
        long next = this.plainSeed * 25214903917L + 11L & 281474976710655L;
        this.plainSeed = next;
        return (int) (next >> 48 - bits);
    }
}
