package net.fly.adrenaline.mixin;

import net.fly.adrenaline.compat.ControlsOptimization;
import net.fly.adrenaline.compat.OptimizationTakeoverRegistry.Optimization;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.ChunkSerializationContext;
import net.fly.adrenaline.util.ChunkSerializationSupport;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkSerializer.class)
public abstract class MixinChunkSerializer {

    private static final ThreadLocal<Deque<ChunkSerializationContext>> adrenaline$serializationContexts = new ThreadLocal<>();

    @Shadow
    private static Codec<PalettedContainer<BlockState>> BLOCK_STATE_CODEC;

    @Shadow
    private static Codec<PalettedContainerRO<Holder<Biome>>> makeBiomeCodec(Registry<Biome> biomeRegistry) {
        return null;
    }

    @WrapMethod(method = "write")
    @ControlsOptimization(Optimization.CHUNK_SERIALIZATION)
    private static CompoundTag adrenaline$writeSections(ServerLevel level, ChunkAccess chunk, Operation<CompoundTag> original) {
        if (!AdrenalineConfig.parallelChunkSerializationEnabled()) {
            return original.call(level, chunk);
        }
        Deque<ChunkSerializationContext> stack = adrenaline$serializationContexts.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            adrenaline$serializationContexts.set(stack);
        }
        stack.push(new ChunkSerializationContext(chunk.getPos(), chunk.getSections()));
        try {
            return original.call(level, chunk);
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                adrenaline$serializationContexts.remove();
            }
        }
    }

    private static ChunkSerializationContext adrenaline$currentContext() {
        Deque<ChunkSerializationContext> stack = adrenaline$serializationContexts.get();
        return stack == null ? null : stack.peek();
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/storage/ChunkSerializer;makeBiomeCodec(Lnet/minecraft/core/Registry;)Lcom/mojang/serialization/Codec;"))
    @ControlsOptimization(Optimization.BIOME_CODEC)
    private static Codec<PalettedContainerRO<Holder<Biome>>> adrenaline$cacheBiomeCodec(Registry<Biome> biomeRegistry) {
        Codec<PalettedContainerRO<Holder<Biome>>> codec = ChunkSerializationSupport.biomeCodec(biomeRegistry, MixinChunkSerializer::makeBiomeCodec);
        ChunkSerializationContext context = adrenaline$currentContext();
        if (context != null && AdrenalineConfig.parallelChunkSerializationEnabled()) {
            context.prepare(BLOCK_STATE_CODEC, codec);
        }
        return codec;
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", ordinal = 2, remap = false))
    private static DataResult<?> adrenaline$encodeBlockStates(Codec<?> codec, DynamicOps<?> ops, Object value) {
        ChunkSerializationContext context = adrenaline$currentContext();
        return context != null ? context.encode(codec, ops, value) : ChunkSerializationSupport.encodeStart(codec, ops, value);
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lcom/mojang/serialization/Codec;encodeStart(Lcom/mojang/serialization/DynamicOps;Ljava/lang/Object;)Lcom/mojang/serialization/DataResult;", ordinal = 3, remap = false))
    private static DataResult<?> adrenaline$encodeBiomes(Codec<?> codec, DynamicOps<?> ops, Object value) {
        ChunkSerializationContext context = adrenaline$currentContext();
        return context != null ? context.encode(codec, ops, value) : ChunkSerializationSupport.encodeStart(codec, ops, value);
    }
}
