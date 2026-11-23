package net.fly.adrenaline.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.level.progress.StoringChunkProgressListener;

public interface ChunkMapRenderer {

    void adrenaline$renderChunkMap(GuiGraphics graphics, StoringChunkProgressListener progressListener,
        int centerX, int centerY, int cellSize, int padding, Runnable renderStages);

    void adrenaline$renderChunkMapOverlays(GuiGraphics graphics, StoringChunkProgressListener progressListener,
        int centerX, int centerY, int cellSize, int padding);
}
