package net.fly.adrenaline.io;

import net.fly.adrenaline.Adrenaline;
import net.fly.datafly.Datafly;
import net.fly.datafly.api.DataTask;
import net.fly.datafly.api.DataWriter;
import net.fly.datafly.api.WriterSettings;
import net.fly.datafly.codec.Codecs;
import net.fly.datafly.lifecycle.LifecycleManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class ChunkStore {

    private static final String WRITER_ID = "adrenaline-chunks";
    private static DataWriter writer;

    private ChunkStore() {
    }

    public static void init() {
        LifecycleManager.initialize();
        writer = Datafly.registerWriter(
                WriterSettings.builder(WRITER_ID)
                        .codec(Codecs.NBT())
                        .coalesceWindow(20, TimeUnit.MILLISECONDS)
                        .emergencyCompression(false)
                        .build()
        );
        Adrenaline.LOGGER.info("ChunkStore initialized, coalesce window: 20ms");
    }

    public static DataTask<Void> write(ChunkPos pos, CompoundTag data, Path regionFolder) {
        return writer.write(chunkPath(pos, regionFolder), data);
    }

    public static CompoundTag read(ChunkPos pos, Path regionFolder) {
        try {
            return writer.read(chunkPath(pos, regionFolder), CompoundTag.class).await();
        } catch (Exception e) {
            return null;
        }
    }

    public static Path chunkPath(ChunkPos pos, Path regionFolder) {
        Path normalizedFolder = regionFolder.toAbsolutePath().normalize();
        String folderName = normalizedFolder.getFileName().toString();
        String folderKey = Integer.toHexString(normalizedFolder.toString().hashCode());
        Path base = FMLPaths.GAMEDIR.get().resolve(".adrenaline").resolve("chunk_cache").resolve(folderName + "-" + folderKey);
        return base.resolve("c." + pos.x + "." + pos.z + ".nbt");
    }
}
