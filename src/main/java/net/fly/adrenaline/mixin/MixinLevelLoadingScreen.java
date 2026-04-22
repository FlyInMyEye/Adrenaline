package net.fly.adrenaline.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelLoadingScreen.class)
public class MixinLevelLoadingScreen {

    @Shadow @Final private static Object2IntMap<ChunkStatus> COLORS;

    private static final int DEFAULT_SPAWN_ZONE_RADIUS = 11;
    private static final int DEFAULT_DIAMETER = (DEFAULT_SPAWN_ZONE_RADIUS + ChunkStatus.maxDistance()) * 2 + 1;

    @Overwrite
    public static void renderChunks(GuiGraphics guiGraphics, StoringChunkProgressListener progressListener, int centerX, int centerY, int cellSize, int padding) {
        int step = cellSize + padding;
        int fullDiameter = progressListener.getFullDiameter();
        int diameter = progressListener.getDiameter();
        int pixels = diameter * step - padding;
        float basePixels = (float) (DEFAULT_DIAMETER * step - padding);
        float scale = basePixels / (float) pixels;
        int borderRadius = (fullDiameter * step - padding) / 2 + 1;
        int startX = -pixels / 2;
        int startY = -pixels / 2;
        int rectSize = step - padding;
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        if (padding != 0) {
            guiGraphics.fill(-borderRadius, -borderRadius, -borderRadius + 1, borderRadius, -16772609);
            guiGraphics.fill(borderRadius - 1, -borderRadius, borderRadius, borderRadius, -16772609);
            guiGraphics.fill(-borderRadius, -borderRadius, borderRadius, -borderRadius + 1, -16772609);
            guiGraphics.fill(-borderRadius, borderRadius - 1, borderRadius, borderRadius, -16772609);
        }

        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                ChunkStatus status = progressListener.getStatus(x, z);
                int drawX = startX + x * step;
                int drawY = startY + z * step;
                guiGraphics.fill(drawX, drawY, drawX + rectSize, drawY + rectSize, COLORS.getInt(status) | -16777216);
            }
        }

        pose.popPose();
    }
}
