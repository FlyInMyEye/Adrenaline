package net.fly.adrenaline.util;

import java.util.Map;
import java.util.HashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldgenDifferenceState {

    private static volatile MinecraftServer server;
    private static volatile ResourceKey<Level> dimension = Level.OVERWORLD;
    private static volatile Map<Long, Difference> differences = Map.of();

    private WorldgenDifferenceState() {
    }

    public static void activate(MinecraftServer activeServer, Map<Long, Difference> blockDifferences) {
        activate(activeServer.overworld(), blockDifferences);
    }

    public static void activate(ServerLevel level, Map<Long, Difference> blockDifferences) {
        differences = Map.copyOf(blockDifferences);
        dimension = level.dimension();
        server = level.getServer();
    }

    public static boolean isActive(MinecraftServer candidate) {
        return candidate != null && candidate == server;
    }

    public static void deactivate(MinecraftServer candidate) {
        if (candidate == server) {
            server = null;
            differences = Map.of();
        }
    }

    public static Difference difference(MinecraftServer candidate, BlockPos pos) {
        return difference(candidate, Level.OVERWORLD, pos);
    }

    public static Difference difference(MinecraftServer candidate, ResourceKey<Level> targetDimension, BlockPos pos) {
        return isActive(candidate) && dimension == targetDimension ? differences.get(pos.asLong()) : null;
    }

    public static void clearChunk(ServerLevel level, ChunkPos pos) {
        if (!isActive(level.getServer()) || dimension != level.dimension()) {
            return;
        }
        Map<Long, Difference> remaining = new HashMap<>(differences);
        remaining.keySet().removeIf(key -> new ChunkPos(BlockPos.of(key)).equals(pos));
        differences = Map.copyOf(remaining);
    }

    public static boolean isTool(ItemStack stack) {
        return stack.is(Items.STICK) && stack.hasCustomHoverName() && "Adrenaline".equals(stack.getHoverName().getString());
    }

    public static void cycle(ServerLevel level, BlockPos pos) {
        Difference difference = difference(level.getServer(), level.dimension(), pos);
        if (difference == null) {
            return;
        }

        BlockState current = level.getBlockState(pos);
        BlockState next;
        if (current.is(Blocks.SOUL_CAMPFIRE)) {
            next = Block.BLOCK_STATE_REGISTRY.byId(difference.vanillaStateId());
        } else if (Block.getId(current) == difference.vanillaStateId()) {
            next = Block.BLOCK_STATE_REGISTRY.byId(difference.adrenalineStateId());
        } else {
            next = Blocks.SOUL_CAMPFIRE.defaultBlockState();
        }
        if (next != null) {
            level.setBlock(pos, next, 3);
        }
    }

    public record Difference(int vanillaStateId, int adrenalineStateId) {
    }
}
