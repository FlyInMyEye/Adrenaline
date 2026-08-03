package net.fly.adrenaline.mixin;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public class MixinBlock {

    @Shadow
    @Final
    @Mutable
    private static LoadingCache<VoxelShape, Boolean> SHAPE_FULL_BLOCK_CACHE;

    private static final ThreadLocal<LoadingCache<VoxelShape, Boolean>> ADRENALINE_SHAPE_CACHE =
        ThreadLocal.withInitial(() -> CacheBuilder.newBuilder()
                .maximumSize(512L)
                .weakKeys()
                .build(new CacheLoader<>() {
                    public Boolean load(VoxelShape shape) {
                        return !Shapes.joinIsNotEmpty(Shapes.block(), shape, BooleanOp.NOT_SAME);
                    }
                }));

    @Inject(method = "isShapeFullBlock", at = @At("HEAD"), cancellable = true)
    private static void useThreadLocalCache(VoxelShape shape, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ADRENALINE_SHAPE_CACHE.get().getUnchecked(shape));
    }
}
