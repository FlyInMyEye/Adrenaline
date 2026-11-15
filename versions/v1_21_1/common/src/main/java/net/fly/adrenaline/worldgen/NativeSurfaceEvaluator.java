package net.fly.adrenaline.worldgen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.fly.adrenaline.mixin.levelgen.AdrenalineMixinSurfaceRulesContextApi;
import net.fly.adrenaline.natives.NativeSurfaceSampler;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public final class NativeSurfaceEvaluator {

    private static final int CAPACITY = 8;
    private final ByteBuffer input = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder());
    private final ByteBuffer output = ByteBuffer.allocateDirect(CAPACITY * 12).order(ByteOrder.nativeOrder());
    private final ByteBuffer cache = ByteBuffer.allocateDirect(16384).order(ByteOrder.nativeOrder());
    private NativeSurfaceProgram program;
    private AdrenalineMixinSurfaceRulesContextApi context;
    private int minY;
    private int x;
    private int z;
    private int top;
    private int count;
    private int row;
    private boolean failed;
    private boolean spans;
    private boolean unsafe;
    private long inputAddress;
    private long outputAddress;
    private long cacheAddress;
    private int columnAvailable;
    private int temperatureX;
    private int temperatureY;
    private int temperatureZ;
    private int columnSteep;
    private BooleanSupplier steepTest;
    private int columnMinimum;
    private double columnSecondary;
    private int columnX;
    private int columnZ;
    private Holder<Biome> lastBiome;
    private int lastBiomeId;
    private Class<?> biomeSupplierType;
    private boolean memoizedBiomeSupplier;

    public void bind(NativeSurfaceProgram program, AdrenalineMixinSurfaceRulesContextApi context, int minY) {
        this.bind(program, context, minY, false);
    }

    public void bind(NativeSurfaceProgram program, AdrenalineMixinSurfaceRulesContextApi context, int minY, boolean spans) {
        this.spans = spans;
        this.unsafe = false;
        this.program = program;
        this.context = context;
        this.minY = minY;
        this.count = 0;
        this.columnAvailable = 0;
        this.steepTest = null;
        this.lastBiome = null;
        this.cache.putLong(0, 0L);
        boolean bound = true;
        if (program.needsSteep()) {
            try {
                this.steepTest = SurfaceRulesContextFactory.createCondition(SurfaceRules.steep(), context);
            } catch (ReflectiveOperationException exception) {
                bound = false;
            }
        }
        if (this.inputAddress == 0L) {
            this.inputAddress = NativeSurfaceSampler.address(this.input);
            this.outputAddress = NativeSurfaceSampler.address(this.output);
            this.cacheAddress = NativeSurfaceSampler.address(this.cache);
        }
        this.failed = !bound || this.inputAddress == 0L || this.outputAddress == 0L || this.cacheAddress == 0L || program.address() == 0L;
    }

    public int evaluate(int x, int y, int z) {
        if (this.failed) {
            return NativeSurfaceProgram.FALLBACK;
        }
        int above = this.context.adrenaline$getStoneDepthAbove();
        int below = this.context.adrenaline$getStoneDepthBelow();
        int water = this.context.adrenaline$getWaterHeight();
        int depth = this.context.adrenaline$getSurfaceDepth();
        int available = 0;
        int minimum = 0;
        double secondary = 0.0D;
        int biome = 0;
        int steep = 0;
        int temperature = 0;
        if (this.spans && this.columnX == x && this.columnZ == z) {
            available = this.columnAvailable;
            minimum = this.columnMinimum;
            secondary = this.columnSecondary;
            steep = this.columnSteep;
        }
        this.columnX = x;
        this.columnZ = z;
        this.columnAvailable = available;
        int currentRow = this.top - y;
        int selected = 0;
        while (selected < this.count && y < this.output.getInt(selected * 12 + 8)) selected++;
        if (this.x == x && this.z == z && currentRow >= 0 && selected < this.count
            && this.input.getInt(4) + currentRow == above && this.input.getInt(8) - currentRow == below
            && this.input.getInt(12) == water && this.input.getInt(16) == depth) {
            int used = this.output.getInt(selected * 12 + 4);
            boolean matches = true;
            inputs:
            for (int order = used >>> 8; order != 0; order >>>= 3) {
                switch (order & 7) {
                    case 1 -> {
                        biome = this.biome();
                        available |= NativeSurfaceProgram.BIOME_INPUT;
                        if (this.input.getInt(32) != biome) { matches = false; break inputs; }
                    }
                    case 2 -> {
                        if ((available & NativeSurfaceProgram.MINIMUM_INPUT) == 0) minimum = this.context.adrenaline$getMinSurfaceLevel();
                        available |= NativeSurfaceProgram.MINIMUM_INPUT;
                        if (this.input.getInt(20) != minimum) { matches = false; break inputs; }
                    }
                    case 3 -> {
                        if ((available & NativeSurfaceProgram.SECONDARY_INPUT) == 0) secondary = this.context.adrenaline$getSurfaceSecondary();
                        available |= NativeSurfaceProgram.SECONDARY_INPUT;
                        if (Double.doubleToRawLongBits(this.input.getDouble(24)) != Double.doubleToRawLongBits(secondary)) { matches = false; break inputs; }
                    }
                    case 4 -> {
                        if ((available & NativeSurfaceProgram.STEEP_INPUT) == 0) steep = this.steepTest.getAsBoolean() ? 1 : 0;
                        available |= NativeSurfaceProgram.STEEP_INPUT;
                        if (this.input.getInt(40) != steep) { matches = false; break inputs; }
                    }
                    case 5 -> {
                        temperature = this.temperature(x, y, z);
                        available |= NativeSurfaceProgram.TEMPERATURE_INPUT;
                        if (this.input.getInt(44) != temperature || this.input.getInt(48) != this.temperatureX
                            || this.input.getInt(52) - currentRow != this.temperatureY || this.input.getInt(56) != this.temperatureZ) { matches = false; break inputs; }
                    }
                    default -> { }
                }
            }
            if (matches) {
                this.row = selected;
                int result = this.output.getInt(selected * 12);
                if (result >= -1) {
                    return result;
                }
                if (result == NativeSurfaceProgram.FALLBACK) {
                    this.count = 0;
                    this.columnAvailable = 0;
                    return result;
                }
                switch (result) {
                    case NativeSurfaceProgram.BIOME -> { if ((available & NativeSurfaceProgram.BIOME_INPUT) == 0) { biome = this.biome(); available |= NativeSurfaceProgram.BIOME_INPUT; } }
                    case NativeSurfaceProgram.MINIMUM -> { if ((available & NativeSurfaceProgram.MINIMUM_INPUT) == 0) { minimum = this.context.adrenaline$getMinSurfaceLevel(); available |= NativeSurfaceProgram.MINIMUM_INPUT; } }
                    case NativeSurfaceProgram.SECONDARY -> { if ((available & NativeSurfaceProgram.SECONDARY_INPUT) == 0) { secondary = this.context.adrenaline$getSurfaceSecondary(); available |= NativeSurfaceProgram.SECONDARY_INPUT; } }
                    case NativeSurfaceProgram.STEEP -> { if ((available & NativeSurfaceProgram.STEEP_INPUT) == 0) { steep = this.steepTest.getAsBoolean() ? 1 : 0; available |= NativeSurfaceProgram.STEEP_INPUT; } }
                    case NativeSurfaceProgram.TEMPERATURE -> { if ((available & NativeSurfaceProgram.TEMPERATURE_INPUT) == 0) { temperature = this.temperature(x, y, z); available |= NativeSurfaceProgram.TEMPERATURE_INPUT; } }
                    default -> { }
                }
            }
        }
        this.count = 0;
        switch (this.program.firstInput()) {
            case NativeSurfaceProgram.BIOME -> { if ((available & NativeSurfaceProgram.BIOME_INPUT) == 0) { biome = this.biome(); available |= NativeSurfaceProgram.BIOME_INPUT; } }
            case NativeSurfaceProgram.MINIMUM -> { if ((available & NativeSurfaceProgram.MINIMUM_INPUT) == 0) { minimum = this.context.adrenaline$getMinSurfaceLevel(); available |= NativeSurfaceProgram.MINIMUM_INPUT; } }
            case NativeSurfaceProgram.SECONDARY -> { if ((available & NativeSurfaceProgram.SECONDARY_INPUT) == 0) { secondary = this.context.adrenaline$getSurfaceSecondary(); available |= NativeSurfaceProgram.SECONDARY_INPUT; } }
            case NativeSurfaceProgram.STEEP -> { if ((available & NativeSurfaceProgram.STEEP_INPUT) == 0) { steep = this.steepTest.getAsBoolean() ? 1 : 0; available |= NativeSurfaceProgram.STEEP_INPUT; } }
            case NativeSurfaceProgram.TEMPERATURE -> { if ((available & NativeSurfaceProgram.TEMPERATURE_INPUT) == 0) { temperature = this.temperature(x, y, z); available |= NativeSurfaceProgram.TEMPERATURE_INPUT; } }
            default -> { }
        }
        for (int retry = 0; retry < 6; retry++) {
            if ((available & NativeSurfaceProgram.BIOME_INPUT) != 0 && biome < 0
                || (available & NativeSurfaceProgram.TEMPERATURE_INPUT) != 0 && temperature < 0) {
                this.columnAvailable = 0;
                return NativeSurfaceProgram.FALLBACK;
            }
            boolean predictedBiome = (available & NativeSurfaceProgram.BIOME_INPUT) == 0 && this.lastBiome != null && this.lastBiomeId >= 0;
            int supplied = available;
            if (predictedBiome) {
                biome = this.lastBiomeId;
                supplied |= NativeSurfaceProgram.BIOME_INPUT;
            }
            this.input.clear();
            this.input.putInt(y).putInt(above).putInt(below).putInt(water).putInt(depth).putInt(minimum)
                .putDouble(secondary).putInt(biome).putInt(supplied).putInt(steep).putInt(temperature)
                .putInt(this.temperatureX).putInt(this.temperatureY).putInt(this.temperatureZ).putInt(0);
            int requested = Math.max(1, (int) Math.min(4096, Math.min((long) below, (long) y - this.minY + 1)));
            int length = NativeSurfaceSampler.column(this.program, this.input, this.inputAddress, this.output, this.outputAddress, this.cache, this.cacheAddress, -requested, x, z);
            if (length < 1) {
                this.failed = true;
                return NativeSurfaceProgram.FALLBACK;
            }
            int result = this.output.getInt(0);
            if (predictedBiome && (this.output.getInt(4) & NativeSurfaceProgram.BIOME_INPUT) != 0) {
                int predicted = biome;
                biome = this.biome();
                available |= NativeSurfaceProgram.BIOME_INPUT;
                if (biome != predicted) continue;
            }
            if (result >= -1) {
                this.x = x;
                this.z = z;
                this.top = y;
                this.row = 0;
                this.count = length;
                this.columnAvailable = available & (NativeSurfaceProgram.MINIMUM_INPUT | NativeSurfaceProgram.SECONDARY_INPUT | NativeSurfaceProgram.STEEP_INPUT);
                this.columnMinimum = minimum;
                this.columnSecondary = secondary;
                this.columnSteep = steep;
                return result;
            }
            if (result == NativeSurfaceProgram.BIOME && (available & NativeSurfaceProgram.BIOME_INPUT) == 0) {
                biome = this.biome();
                available |= NativeSurfaceProgram.BIOME_INPUT;
            } else if (result == NativeSurfaceProgram.MINIMUM && (available & NativeSurfaceProgram.MINIMUM_INPUT) == 0) {
                minimum = this.context.adrenaline$getMinSurfaceLevel();
                available |= NativeSurfaceProgram.MINIMUM_INPUT;
            } else if (result == NativeSurfaceProgram.SECONDARY && (available & NativeSurfaceProgram.SECONDARY_INPUT) == 0) {
                secondary = this.context.adrenaline$getSurfaceSecondary();
                available |= NativeSurfaceProgram.SECONDARY_INPUT;
            } else if (result == NativeSurfaceProgram.STEEP && (available & NativeSurfaceProgram.STEEP_INPUT) == 0) {
                steep = this.steepTest.getAsBoolean() ? 1 : 0;
                available |= NativeSurfaceProgram.STEEP_INPUT;
            } else if (result == NativeSurfaceProgram.TEMPERATURE && (available & NativeSurfaceProgram.TEMPERATURE_INPUT) == 0) {
                temperature = this.temperature(x, y, z);
                available |= NativeSurfaceProgram.TEMPERATURE_INPUT;
            } else {
                this.columnAvailable = 0;
                return NativeSurfaceProgram.FALLBACK;
            }
        }
        this.columnAvailable = 0;
        return NativeSurfaceProgram.FALLBACK;
    }

    public boolean unsafe() {
        return this.unsafe;
    }

    public int bottom(int y) {
        return this.output.getInt(this.row * 12 + 8);
    }

    public boolean usesBiome() {
        return (this.output.getInt(this.row * 12 + 4) & (NativeSurfaceProgram.BIOME_INPUT | NativeSurfaceProgram.TEMPERATURE_INPUT)) != 0;
    }

    public BlockState state(int index) {
        return this.program.state(index);
    }

    private int temperature(int x, int y, int z) {
        Supplier<Holder<Biome>> supplier = this.context.adrenaline$getBiome();
        if ((!this.memoized(supplier) && !SurfacePointEvaluator.canCacheBiome(this.context))
            || x < -33554432 || x > 33554431 || z < -33554432 || z > 33554431 || y < -2048 || y > 2047) {
            this.unsafe = true;
            this.spans = false;
            return -1;
        }
        Holder<Biome> biome = supplier.get();
        int id = this.program.temperatureId(biome);
        if (id >= 0) {
            var originalPosition = this.context.adrenaline$getPosition();
            if (originalPosition.getClass() != net.minecraft.core.BlockPos.MutableBlockPos.class) {
                this.unsafe = true;
                this.spans = false;
                return -1;
            }
            var position = this.program.temperaturePosition(biome, originalPosition.set(x, y, z));
            this.temperatureX = position.getX();
            this.temperatureY = position.getY();
            this.temperatureZ = position.getZ();
            if (this.temperatureX < -33554432 || this.temperatureX > 33554431 || this.temperatureZ < -33554432 || this.temperatureZ > 33554431
                || this.temperatureY < -2048 || this.temperatureY > 2047) id = -1;
        }
        if (id < 0) {
            this.unsafe = true;
            this.spans = false;
        }
        return id;
    }

    private int biome() {
        Supplier<Holder<Biome>> supplier = this.context.adrenaline$getBiome();
        if (!this.memoized(supplier) && !SurfacePointEvaluator.canCacheBiome(this.context)) {
            this.unsafe = true;
            this.spans = false;
            return -1;
        }
        Holder<Biome> holder = supplier.get();
        int id;
        if (holder != null && holder == this.lastBiome) {
            id = this.lastBiomeId;
        } else {
            id = this.program.biomeId(holder);
            this.lastBiome = holder;
            this.lastBiomeId = id;
        }
        if (id < 0) {
            this.unsafe = true;
            this.spans = false;
        }
        return id;
    }

    private boolean memoized(Supplier<Holder<Biome>> supplier) {
        Class<?> type = supplier.getClass();
        if (type != this.biomeSupplierType) {
            this.biomeSupplierType = type;
            this.memoizedBiomeSupplier = SurfaceBiomeSuppliers.memoized(type);
        }
        return this.memoizedBiomeSupplier;
    }

    public void clear() {
        this.failed = true;
        this.program = null;
        this.context = null;
        this.steepTest = null;
        this.lastBiome = null;
        this.count = 0;
    }

}
