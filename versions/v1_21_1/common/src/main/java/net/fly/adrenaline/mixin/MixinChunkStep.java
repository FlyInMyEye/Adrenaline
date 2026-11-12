package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.util.WorldgenStageStats;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChunkStep.class)
public abstract class MixinChunkStep {

    @WrapMethod(method = "apply")
    private CompletableFuture<ChunkAccess> adrenaline$timeStep(WorldGenContext context,
            StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk, Operation<CompletableFuture<ChunkAccess>> original) {
        ChunkStatus status = ((ChunkStep) (Object) this).targetStatus();
        if (!BuildConfig.DEBUG || status == ChunkStatus.EMPTY || status == ChunkStatus.FULL
                || chunk.getPersistedStatus().isOrAfter(status)) {
            return original.call(context, cache, chunk);
        }
        long started = WorldgenStageStats.beginStage(chunk.getPos(), status);
        return WorldgenStageStats.track(status, chunk.getPos(), started, original.call(context, cache, chunk));
    }
}
