package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
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
                if (SurfaceRuleHooks.hooked(Heightmap.class, method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method))) {
                    return false;
                }
                found = true;
            }
        }
        return found;
    }

    @ModifyExpressionValue(
        method = "setBlockState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkStatus;heightmapsAfter()Ljava/util/EnumSet;")
    )
    private EnumSet<Heightmap.Types> adrenaline$skipUnchangedHeightmaps(
        EnumSet<Heightmap.Types> types,
        @Local(argsOnly = true) BlockPos pos,
        @Local(argsOnly = true) BlockState state,
        @Local(ordinal = 1) BlockState previous
    ) {
        if (state != previous || !adrenaline$unmodifiedHeightmaps || !AdrenalineConfig.heightmapOptimizationsEnabled()) {
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
