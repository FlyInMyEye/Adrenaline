package net.fly.adrenaline.mixin;

import net.fly.adrenaline.util.DebugChunkGeneration;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
public class MixinServerChunkCacheDebug {

    @Shadow @Final private ServerLevel level;

    @Inject(method = "getGenerator", at = @At("HEAD"), cancellable = true)
    private void adrenaline$debugGenerator(CallbackInfoReturnable<ChunkGenerator> cir) {
        DebugChunkGeneration generation = DebugChunkGeneration.current(this.level);
        if (generation != null) {
            cir.setReturnValue(generation.generator());
        }
    }

    @Inject(method = "randomState", at = @At("HEAD"), cancellable = true)
    private void adrenaline$debugRandomState(CallbackInfoReturnable<RandomState> cir) {
        DebugChunkGeneration generation = DebugChunkGeneration.current(this.level);
        if (generation != null) {
            cir.setReturnValue(generation.randomState());
        }
    }

    @Inject(method = "getGeneratorState", at = @At("HEAD"), cancellable = true)
    private void adrenaline$debugGeneratorState(CallbackInfoReturnable<ChunkGeneratorStructureState> cir) {
        DebugChunkGeneration generation = DebugChunkGeneration.current(this.level);
        if (generation != null) {
            cir.setReturnValue(generation.generatorState());
        }
    }
}
