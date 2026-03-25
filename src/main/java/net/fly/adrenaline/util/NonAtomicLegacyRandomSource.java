package net.fly.adrenaline.util;

import net.minecraft.world.level.levelgen.LegacyRandomSource;

public final class NonAtomicLegacyRandomSource extends LegacyRandomSource {

    private long plainSeed;

    public NonAtomicLegacyRandomSource(long seed) {
        super(seed);
        this.plainSeed = getRawSeed();
    }

    private long getRawSeed() {
        try {
            var field = LegacyRandomSource.class.getDeclaredField("seed");
            field.setAccessible(true);
            java.util.concurrent.atomic.AtomicLong atomicSeed =
                (java.util.concurrent.atomic.AtomicLong) field.get(this);
            return atomicSeed.get();
        } catch (Exception e) {
            return 0L;
        }
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
