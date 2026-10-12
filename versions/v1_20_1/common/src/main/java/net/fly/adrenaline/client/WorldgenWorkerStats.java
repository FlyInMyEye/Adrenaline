package net.fly.adrenaline.client;

import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class WorldgenWorkerStats {

    private WorldgenWorkerStats() {
    }

    public static String line() {
        ChunkJobScheduler scheduler = ChunkJobScheduler.get();
        return Component.translatable("gui.adrenaline.worldgen_stats.workers", scheduler.parallelism(), scheduler.configuredParallelism()).getString();
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() == null) {
            return;
        }
        String line = line();
        int width = minecraft.font.width(line);
        int x = graphics.guiWidth() - width - 2;
        graphics.fill(x - 1, 1, x + width + 1, minecraft.font.lineHeight + 1, -1873784752);
        graphics.drawString(minecraft.font, line, x, 2, 14737632, false);
    }
}
