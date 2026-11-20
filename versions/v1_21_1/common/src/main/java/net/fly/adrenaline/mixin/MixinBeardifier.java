package net.fly.adrenaline.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.AdrenalineCompiledBeardifierAccess;
import net.fly.adrenaline.worldgen.AdrenalineEmptyBeardifierAccess;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Beardifier.class)
public abstract class MixinBeardifier implements AdrenalineCompiledBeardifierAccess, AdrenalineEmptyBeardifierAccess {

    @Shadow private ObjectListIterator<?> pieceIterator;
    @Shadow private ObjectListIterator<JigsawJunction> junctionIterator;
    @Shadow private static double getBuryContribution(double x, double y, double z) { return 0.0D; }
    @Shadow private static double getBeardContribution(int x, int y, int z, int dy) { return 0.0D; }

    @Unique
    private static final double[] adrenaline$beardContributions = adrenaline$createBeardContributions();

    @Unique
    private static double[] adrenaline$createBeardContributions() {
        double[] values = new double[24 * 24 * 24];
        for (int z = 0; z < 24; z++) {
            for (int x = 0; x < 24; x++) {
                for (int y = 0; y < 24; y++) {
                    values[z * 24 * 24 + x * 24 + y] = getBeardContribution(x - 12, y - 12, z - 12, y - 12);
                }
            }
        }
        return values;
    }

    @Unique
    private static double adrenaline$beardContribution(int x, int y, int z, int dy) {
        if (dy != y) {
            return getBeardContribution(x, y, z, dy);
        }
        int kernelX = x + 12;
        int kernelY = y + 12;
        int kernelZ = z + 12;
        if (kernelX < 0 || kernelX >= 24 || kernelY < 0 || kernelY >= 24 || kernelZ < 0 || kernelZ >= 24) {
            return 0.0D;
        }
        return adrenaline$beardContributions[kernelZ * 24 * 24 + kernelX * 24 + kernelY];
    }

    @Unique
    private Beardifier.Rigid[] adrenaline$pieces;

    @Unique
    private JigsawJunction[] adrenaline$junctions;

    @Unique
    private void adrenaline$initCaches() {
        if (this.adrenaline$pieces == null) {
            ObjectArrayList<Beardifier.Rigid> pieces = new ObjectArrayList<>();
            while (this.pieceIterator.hasNext()) {
                pieces.add((Beardifier.Rigid) this.pieceIterator.next());
            }
            this.pieceIterator.back(Integer.MAX_VALUE);
            this.adrenaline$pieces = pieces.toArray(Beardifier.Rigid[]::new);
        }
        if (this.adrenaline$junctions == null) {
            ObjectArrayList<JigsawJunction> junctions = new ObjectArrayList<>();
            while (this.junctionIterator.hasNext()) {
                junctions.add(this.junctionIterator.next());
            }
            this.junctionIterator.back(Integer.MAX_VALUE);
            this.adrenaline$junctions = junctions.toArray(JigsawJunction[]::new);
        }
    }

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    @ControlsOptimization(Optimization.BEARDIFIER)
    private void adrenaline$compute(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        if (!AdrenalineConfig.beardifierOptimizationsEnabled()) {
            return;
        }

        cir.setReturnValue(this.adrenaline$computeDirect(context));
    }

    @Override
    public double adrenaline$computeDirect(DensityFunction.FunctionContext context) {
        this.adrenaline$initCaches();
        int blockX = context.blockX();
        int blockY = context.blockY();
        int blockZ = context.blockZ();
        double value = 0.0D;

        Beardifier.Rigid[] pieces = this.adrenaline$pieces;
        for (int i = 0; i < pieces.length; i++) {
            Beardifier.Rigid rigid = pieces[i];
            BoundingBox box = rigid.box();
            int groundLevelDelta = rigid.groundLevelDelta();
            int xDistance = Math.max(0, Math.max(box.minX() - blockX, blockX - box.maxX()));
            int zDistance = Math.max(0, Math.max(box.minZ() - blockZ, blockZ - box.maxZ()));
            int minY = box.minY() + groundLevelDelta;
            int yDistance = blockY - minY;
            TerrainAdjustment terrainAdjustment = rigid.terrainAdjustment();
            int adjustmentDistance;
            switch (terrainAdjustment) {
                case NONE -> adjustmentDistance = 0;
                case BURY, BEARD_THIN -> adjustmentDistance = yDistance;
                case BEARD_BOX -> adjustmentDistance = Math.max(0, Math.max(minY - blockY, blockY - box.maxY()));
                case ENCAPSULATE -> adjustmentDistance = Math.max(0, Math.max(box.minY() - blockY, blockY - box.maxY()));
                default -> throw new IncompatibleClassChangeError();
            }

            value += switch (terrainAdjustment) {
                case NONE -> 0.0D;
                case BURY -> getBuryContribution(xDistance, adjustmentDistance / 2.0D, zDistance);
                case BEARD_THIN, BEARD_BOX -> adrenaline$beardContribution(xDistance, adjustmentDistance, zDistance, yDistance) * 0.8D;
                case ENCAPSULATE -> getBuryContribution(xDistance / 2.0D, adjustmentDistance / 2.0D, zDistance / 2.0D) * 0.8D;
                default -> throw new IncompatibleClassChangeError();
            };
        }

        JigsawJunction[] junctions = this.adrenaline$junctions;
        for (int i = 0; i < junctions.length; i++) {
            JigsawJunction junction = junctions[i];
            int xDistance = blockX - junction.getSourceX();
            int yDistance = blockY - junction.getSourceGroundY();
            int zDistance = blockZ - junction.getSourceZ();
            value += adrenaline$beardContribution(xDistance, yDistance, zDistance, yDistance) * 0.4D;
        }

        return value;
    }

    @Override
    public long adrenaline$firstClearY() {
        this.adrenaline$initCaches();
        long firstClearY = Long.MIN_VALUE;
        for (Beardifier.Rigid piece : this.adrenaline$pieces) {
            BoundingBox box = piece.box();
            long groundY = (long) box.minY() + piece.groundLevelDelta();
            if (Math.abs(groundY) >= 0x10000000L || Math.abs((long) box.maxY()) >= 0x10000000L
                || Math.abs((long) box.minY()) >= 0x10000000L) {
                return Long.MAX_VALUE;
            }
            firstClearY = Math.max(firstClearY, Math.max(groundY, box.maxY()) + Beardifier.BEARD_KERNEL_RADIUS);
        }
        for (JigsawJunction junction : this.adrenaline$junctions) {
            int groundY = junction.getSourceGroundY();
            if (Math.abs((long) groundY) >= 0x10000000L) {
                return Long.MAX_VALUE;
            }
            firstClearY = Math.max(firstClearY, (long) groundY + Beardifier.BEARD_KERNEL_RADIUS);
        }
        return firstClearY;
    }

    @Override
    public boolean adrenaline$isEmpty() {
        this.adrenaline$initCaches();
        return this.adrenaline$pieces.length == 0 && this.adrenaline$junctions.length == 0;
    }
}
