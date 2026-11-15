package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.chunk.LevelChunkSection;
import java.lang.invoke.MethodType;
import java.util.EnumMap;
import java.util.EnumSet;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.worldgen.SurfaceRuleHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ProtoChunk.class)
public abstract class MixinProtoChunkHeightmaps {

    @Unique
    private static final EnumSet<Heightmap.Types> adrenaline$unchangedHeightmaps = EnumSet.noneOf(Heightmap.Types.class);

    @Unique
    private static final boolean adrenaline$unmodifiedHeightmaps = adrenaline$heightmapsUnmodified();

    @Unique
    private static boolean adrenaline$heightmapsUnmodified() {
        boolean found = false;
        for (var method : Heightmap.class.getDeclaredMethods()) {
            if (method.getReturnType() == boolean.class
                && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class, int.class, int.class, BlockState.class})) {
                String descriptor = MethodType.methodType(method.getReturnType(), method.getParameterTypes()).descriptorString();
                if (SurfaceRuleHooks.hooked(Heightmap.class, method.getName() + descriptor)) {
                    return false;
                }
                found = true;
            }
        }
        return found;
    }

    @WrapOperation(method = "setBlockState", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState adrenaline$trackUnchangedBlock(LevelChunkSection section, int x, int y, int z, BlockState state,
            Operation<BlockState> original, @Share("adrenaline$unchangedBlock") LocalBooleanRef unchanged) {
        BlockState previous = original.call(section, x, y, z, state);
        unchanged.set(previous == state);
        return previous;
    }

    @ModifyExpressionValue(
        method = "setBlockState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/status/ChunkStatus;heightmapsAfter()Ljava/util/EnumSet;")
    )
    @ControlsOptimization(Optimization.HEIGHTMAP)
    private EnumSet<Heightmap.Types> adrenaline$skipUnchangedHeightmaps(
        EnumSet<Heightmap.Types> types,
        @Local(argsOnly = true) BlockPos pos,
        @Share("adrenaline$unchangedBlock") LocalBooleanRef unchanged
    ) {
        if (!unchanged.get() || !adrenaline$unmodifiedHeightmaps || !AdrenalineConfig.heightmapOptimizationsEnabled()) {
            return types;
        }
        var heightmaps = ((MixinChunkAccessAccessor) this).adrenaline$getHeightmaps();
        if (heightmaps.getClass() != EnumMap.class) {
            return types;
        }
        int x = pos.getX() & 15;
        int z = pos.getZ() & 15;
        int top = pos.getY() + 1;
        for (Heightmap.Types type : types) {
            Heightmap heightmap = heightmaps.get(type);
            if (heightmap == null || heightmap.getClass() != Heightmap.class || heightmap.getFirstAvailable(x, z) <= top) {
                return types;
            }
        }
        return adrenaline$unchangedHeightmaps;
    }
}
