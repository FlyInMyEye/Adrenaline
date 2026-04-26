package net.fly.adrenaline.mixin;

import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LevelLoadingScreen.class)
public class MixinLevelLoadingScreen {

    private static final int DEFAULT_SPAWN_ZONE_RADIUS = 11;
    private static final int DEFAULT_DIAMETER = (DEFAULT_SPAWN_ZONE_RADIUS + ChunkStatus.maxDistance()) * 2 + 1;

    @Shadow
    private String getFormattedProgress() {
        return null;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V"))
    private void adrenaline$renderScaledChunkMap(GuiGraphics guiGraphics, StoringChunkProgressListener progressListener, int centerX, int centerY, int cellSize, int padding) {
        if (AdrenalineConfig.resolvedSpawnZoneRadius() == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            return;
        }

        int step = cellSize + padding;
        int diameter = progressListener.getDiameter();
        int pixels = diameter * step - padding;
        if (pixels <= 0) {
            LevelLoadingScreen.renderChunks(guiGraphics, progressListener, centerX, centerY, cellSize, padding);
            return;
        }

        float basePixels = (float) (DEFAULT_DIAMETER * step - padding);
        float scale = basePixels / (float) pixels;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.pose().translate(-centerX, -centerY, 0.0F);
        try {
            LevelLoadingScreen.renderChunks(guiGraphics, progressListener, centerX, centerY, cellSize, padding);
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;getFormattedProgress()Ljava/lang/String;"))
    private String adrenaline$replaceInstantProgressText(LevelLoadingScreen screen) {
        return AdrenalineConfig.resolvedSpawnZoneRadius() == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? "Searching for spawnpoint" : this.getFormattedProgress();
    }
}
