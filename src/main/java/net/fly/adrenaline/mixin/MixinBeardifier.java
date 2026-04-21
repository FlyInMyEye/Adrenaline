package net.fly.adrenaline.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
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
public abstract class MixinBeardifier {

    @Shadow private ObjectListIterator<?> pieceIterator;
    @Shadow private ObjectListIterator<JigsawJunction> junctionIterator;
    @Shadow private static double getBuryContribution(int x, int y, int z) { return 0.0D; }
    @Shadow private static double getBeardContribution(int x, int y, int z, int dy) { return 0.0D; }

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
    private void adrenaline$compute(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
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
                default -> throw new IncompatibleClassChangeError();
            }

            value += switch (terrainAdjustment) {
                case NONE -> 0.0D;
                case BURY -> getBuryContribution(xDistance, adjustmentDistance, zDistance);
                case BEARD_THIN, BEARD_BOX -> getBeardContribution(xDistance, adjustmentDistance, zDistance, yDistance) * 0.8D;
                default -> throw new IncompatibleClassChangeError();
            };
        }

        JigsawJunction[] junctions = this.adrenaline$junctions;
        for (int i = 0; i < junctions.length; i++) {
            JigsawJunction junction = junctions[i];
            int xDistance = blockX - junction.getSourceX();
            int yDistance = blockY - junction.getSourceGroundY();
            int zDistance = blockZ - junction.getSourceZ();
            value += getBeardContribution(xDistance, yDistance, zDistance, yDistance) * 0.4D;
        }

        cir.setReturnValue(value);
    }
}
