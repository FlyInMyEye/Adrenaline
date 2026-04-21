package net.fly.adrenaline.mixin;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseChunk.class)
public class MixinNoiseChunkOreVeinifier {

    private static final BlockState COPPER_ORE = Blocks.COPPER_ORE.defaultBlockState();
    private static final BlockState RAW_COPPER_BLOCK = Blocks.RAW_COPPER_BLOCK.defaultBlockState();
    private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
    private static final BlockState DEEPSLATE_IRON_ORE = Blocks.DEEPSLATE_IRON_ORE.defaultBlockState();
    private static final BlockState RAW_IRON_BLOCK = Blocks.RAW_IRON_BLOCK.defaultBlockState();
    private static final BlockState TUFF = Blocks.TUFF.defaultBlockState();

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/OreVeinifier;create(Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;)Lnet/minecraft/world/level/levelgen/NoiseChunk$BlockStateFiller;"
        )
    )
    private NoiseChunk.BlockStateFiller adrenaline$fastOreVeinifier(DensityFunction veinToggle, DensityFunction veinRidged, DensityFunction veinGap, PositionalRandomFactory randomFactory) {
        return context -> {
            double veininess = veinToggle.compute(context);
            int blockY = context.blockY();
            boolean copper = veininess > 0.0D;
            int maxY = copper ? 50 : -8;
            int minY = copper ? 0 : -60;
            int maxDelta = maxY - blockY;
            int minDelta = blockY - minY;
            if (minDelta < 0 || maxDelta < 0) {
                return null;
            }

            double absVeininess = Math.abs(veininess);
            int edge = Math.min(maxDelta, minDelta);
            double edgeRoundoff = Mth.clampedMap(edge, 0.0D, 20.0D, -0.2D, 0.0D);
            if (absVeininess + edgeRoundoff < 0.4000000059604645D) {
                return null;
            }

            RandomSource random = randomFactory.at(context.blockX(), blockY, context.blockZ());
            if (random.nextFloat() > 0.7F) {
                return null;
            }
            if (veinRidged.compute(context) >= 0.0D) {
                return null;
            }

            double richness = Mth.clampedMap(absVeininess, 0.4000000059604645D, 0.6000000238418579D, 0.10000000149011612D, 0.30000001192092896D);
            if (random.nextFloat() < richness && veinGap.compute(context) > -0.30000001192092896D) {
                if (copper) {
                    return random.nextFloat() < 0.02F ? RAW_COPPER_BLOCK : COPPER_ORE;
                }
                return random.nextFloat() < 0.02F ? RAW_IRON_BLOCK : DEEPSLATE_IRON_ORE;
            }

            return copper ? GRANITE : TUFF;
        };
    }
}
