package net.fly.adrenaline.util;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.mixin.MixinChunkAccessAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelChunkTicks;

public final class DebugChunkCommands {

    private DebugChunkCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return Commands.literal("chunk")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("make")
                .then(operation("adrenaline", true, false))
                .then(operation("vanilla", false, false)))
            .then(operation("highlight", true, true));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> stickCommand() {
        return Commands.literal("stick")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                ItemStack tool = new ItemStack(Items.STICK);
                tool.setHoverName(Component.literal("Adrenaline"));
                if (!player.getInventory().add(tool)) {
                    player.drop(tool, false);
                }
                player.containerMenu.broadcastChanges();
                return 1;
            });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> operation(String name, boolean optimized, boolean highlight) {
        return Commands.literal(name)
            .executes(context -> run(context.getSource(), context.getSource().getPlayerOrException().blockPosition(), optimized, highlight))
            .then(Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(context -> run(context.getSource(), BlockPosArgument.getBlockPos(context, "pos"), optimized, highlight)));
    }

    private static int run(CommandSourceStack source, BlockPos position, boolean optimized, boolean highlight) throws CommandSyntaxException {
        ServerLevel level = source.getLevel();
        ChunkPos pos = new ChunkPos(position);
        if (!level.getWorldBorder().isWithinBounds(pos) || !level.isInWorldBounds(position)) {
            source.sendFailure(Component.literal("Target position is outside the world bounds"));
            return 0;
        }
        if (highlight && !level.getServer().isSingleplayer()) {
            source.sendFailure(Component.literal("Chunk highlighting requires an integrated server for the difference HUD"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Generating chunk " + pos + (highlight ? " in both modes" : optimized ? " with Adrenaline" : " without Adrenaline")
            + "; existing blocks and non-player entities will be replaced after backup"), false);
        Path backup = null;
        try {
            DebugChunkGeneration.Result vanilla = highlight ? DebugChunkGeneration.generate(level, pos, false) : null;
            DebugChunkGeneration.Result result = DebugChunkGeneration.generate(level, pos, optimized);
            Map<Long, WorldgenDifferenceState.Difference> differences = new HashMap<>();
            int noiseDifferences = 0;
            if (vanilla != null) {
                for (int i = 0; i < result.noise().length; i++) {
                    if (vanilla.noise()[i] != result.noise()[i]) {
                        noiseDifferences++;
                    }
                }
                for (BlockPos block : positions(level, pos)) {
                    BlockState expected = vanilla.chunk().getBlockState(block);
                    BlockState actual = result.chunk().getBlockState(block);
                    if (expected != actual) {
                        differences.put(block.asLong(), new WorldgenDifferenceState.Difference(Block.getId(expected), Block.getId(actual)));
                    }
                }
            }
            LevelChunk target = level.getChunk(pos.x, pos.z);
            List<Entity> entities = entities(level, pos);
            backup = backup(level, target, entities);
            WorldgenDifferenceState.clearChunk(level, pos);
            replace(level, target, result.chunk(), entities);
            if (highlight) {
                WorldgenDifferenceState.activate(level, differences);
                for (long key : differences.keySet()) {
                    level.setBlock(BlockPos.of(key), Blocks.SOUL_CAMPFIRE.defaultBlockState(), 2 | 16 | 32);
                }
                if (source.getEntity() instanceof ServerPlayer player) {
                    ItemStack tool = new ItemStack(Items.STICK);
                    tool.setHoverName(Component.literal("Adrenaline"));
                    if (!player.getInventory().add(tool)) {
                        player.drop(tool, false);
                    }
                }
            }
            movePlayersOutOfBlocks(level, pos);
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(target, level.getChunkSource().getLightEngine(), null, null);
            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(pos, false)) {
                player.connection.send(packet);
            }
            int noiseCount = noiseDifferences;
            Path saved = backup;
            source.sendSuccess(() -> Component.literal("Chunk " + pos + " replaced. Backup: " + saved
                + (highlight ? ". NOISE differences: " + noiseCount + "; FULL differences: " + differences.size() : "")), false);
            return 1;
        } catch (Exception error) {
            GlobalCommon.LOGGER.error("Chunk command failed for {} in {}", pos, level.dimension().location(), error);
            String recovery = backup == null ? "Live chunk was not replaced." : "Replacement may be incomplete. Backup: " + backup;
            source.sendFailure(Component.literal("Chunk command failed: " + error.getMessage() + ". " + recovery));
            return 0;
        }
    }

    private static Iterable<BlockPos> positions(ServerLevel level, ChunkPos pos) {
        return BlockPos.betweenClosed(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
            pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ());
    }

    private static List<Entity> entities(ServerLevel level, ChunkPos pos) {
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Player) && entity.chunkPosition().equals(pos)
                && entity.getRootVehicle().getSelfAndPassengers().noneMatch(passenger -> passenger instanceof Player)) {
                entities.add(entity);
            }
        }
        return entities;
    }

    private static Path backup(ServerLevel level, LevelChunk chunk, List<Entity> entities) throws IOException {
        CompoundTag backup = new CompoundTag();
        backup.put("Chunk", ChunkSerializer.write(level, chunk));
        backup.putString("Dimension", level.dimension().location().toString());
        ListTag entityData = new ListTag();
        for (Entity entity : entities) {
            CompoundTag tag = new CompoundTag();
            if (entity.saveAsPassenger(tag)) {
                entityData.add(tag);
            }
        }
        backup.put("Entities", entityData);
        Path directory = level.getServer().getWorldPath(LevelResource.ROOT).resolve("adrenaline-chunk-backups");
        Files.createDirectories(directory);
        Path path = directory.resolve(chunk.getPos().x + "_" + chunk.getPos().z + "_" + UUID.randomUUID() + ".nbt");
        NbtIo.writeCompressed(backup, path.toFile());
        return path;
    }

    private static void replace(ServerLevel level, LevelChunk target, ProtoChunk generated, List<Entity> entities) {
        ChunkPos pos = target.getPos();
        BoundingBox bounds = new BoundingBox(pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
            pos.getMaxBlockX(), level.getMaxBuildHeight() - 1, pos.getMaxBlockZ());
        level.getBlockTicks().clearArea(bounds);
        level.getFluidTicks().clearArea(bounds);
        level.clearBlockEvents(bounds);
        ((MixinChunkAccessAccessor) target).adrenaline$getPendingBlockEntities().clear();
        for (BlockPos block : new ArrayList<>(target.getBlockEntitiesPos())) {
            level.removeBlockEntity(block);
        }
        for (BlockPos block : positions(level, pos)) {
            level.setBlock(block, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
        }
        for (Entity entity : entities) {
            entity.discard();
        }
        for (BlockPos block : positions(level, pos)) {
            BlockState state = generated.getBlockState(block);
            if (state != Blocks.AIR.defaultBlockState()) {
                level.setBlock(block, state, 2 | 16 | 32);
            }
        }
        target.fillBiomesFromNoise((x, y, z, sampler) -> generated.getNoiseBiome(x, y, z), level.getChunkSource().randomState().sampler());
        target.setAllStarts(generated.getAllStarts());
        target.setAllReferences(generated.getAllReferences());
        level.onStructureStartsAvailable(target);
        target.setInhabitedTime(0L);
        for (BlockPos block : generated.getBlockEntitiesPos()) {
            CompoundTag tag = generated.getBlockEntityNbtForSaving(block);
            if (tag != null && !"DUMMY".equals(tag.getString("id"))) {
                BlockEntity entity = BlockEntity.loadStatic(block, generated.getBlockState(block), tag);
                if (entity != null) {
                    target.addAndRegisterBlockEntity(entity);
                }
            }
        }
        LevelChunkTicks<Block> blockTicks = generated.unpackBlockTicks();
        blockTicks.unpack(level.getGameTime());
        blockTicks.getAll().forEach(level.getBlockTicks()::schedule);
        LevelChunkTicks<Fluid> fluidTicks = generated.unpackFluidTicks();
        fluidTicks.unpack(level.getGameTime());
        fluidTicks.getAll().forEach(level.getFluidTicks()::schedule);
        for (int i = 0; i < target.getPostProcessing().length; i++) {
            target.getPostProcessing()[i] = generated.getPostProcessing()[i];
        }
        target.postProcessGeneration();
        Heightmap.primeHeightmaps(target, target.getStatus().heightmapsAfter());
        target.initializeLightSources();
        target.setUnsaved(true);
        level.addWorldGenChunkEntities(EntityType.loadEntitiesRecursive(generated.getEntities(), level));
    }

    private static void movePlayersOutOfBlocks(ServerLevel level, ChunkPos pos) {
        for (ServerPlayer player : level.players()) {
            if (player.chunkPosition().equals(pos) && !player.isSpectator()
                && (!level.getBlockState(player.blockPosition()).isAir() || !level.getBlockState(player.blockPosition().above()).isAir())) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
                player.teleportTo(player.getX(), y + 1.0D, player.getZ());
            }
        }
    }
}
