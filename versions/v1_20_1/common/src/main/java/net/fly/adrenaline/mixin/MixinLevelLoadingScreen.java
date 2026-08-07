package net.fly.adrenaline.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.client.AprilFoolsEasterEgg;
import net.fly.adrenaline.client.LevelLoadingScreenExtension;
import net.fly.adrenaline.client.WorldDeletion;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.EarlyWorldEntry;
import net.fly.adrenaline.util.WorldLoadCancellation;
import net.fly.adrenaline.util.WorldgenChunkPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen extends Screen implements LevelLoadingScreenExtension {

    private static final int DEFAULT_SPAWN_ZONE_RADIUS = 11;
    private static final int DEFAULT_DIAMETER = (DEFAULT_SPAWN_ZONE_RADIUS + ChunkStatus.maxDistance()) * 2 + 1;
    private static final int CHUNK_PREVIEW_HORIZONTAL_MARGIN = 32;
    private static final int CHUNK_PREVIEW_VERTICAL_MARGIN = 112;

    @Shadow
    private String getFormattedProgress() {
        return null;
    }

    @Shadow
    @Final
    private StoringChunkProgressListener progressListener;

    @Unique
    private Button adrenaline$cancelButton;

    @Unique
    private Button adrenaline$earlyEntryButton;

    @Unique
    private DynamicTexture adrenaline$chunkPreviewTexture;

    @Unique
    private ResourceLocation adrenaline$chunkPreviewTextureLocation;

    @Unique
    private int adrenaline$chunkPreviewTextureDiameter;

    @Unique
    private boolean[] adrenaline$uploadedChunkPreviews;

    protected MixinLevelLoadingScreen(Component title) {
        super(title);
    }

    @Override
    public void adrenaline$addJoiningControls() {
        boolean hideJoiningControls = !WorldLoadCancellation.isNewWorld() && AdrenalineConfig.fastTerrainLoadingMode() != AdrenalineConfig.FastTerrainLoadingMode.OFF;
        AdrenalineConfig.StartBeforehandMode startMode = AdrenalineConfig.startBeforehandMode();
        if (!hideJoiningControls && startMode != AdrenalineConfig.StartBeforehandMode.OFF) {
            Component message = Component.translatable(startMode == AdrenalineConfig.StartBeforehandMode.BORING ? "gui.adrenaline.enter_early_boring" : "gui.adrenaline.enter_early");
            this.adrenaline$earlyEntryButton = this.addRenderableWidget(Button.builder(message, this::adrenaline$requestEarlyEntry).bounds((this.width - 160) / 2, this.height - 52, 160, 20).build());
        } else {
            this.adrenaline$earlyEntryButton = null;
        }
        if (!hideJoiningControls && AdrenalineConfig.showCancelButton()) {
            this.adrenaline$cancelButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.cancel"), this::adrenaline$cancelWorldCreation).bounds((this.width - 100) / 2, this.height - 28, 100, 20).build());
        } else {
            this.adrenaline$cancelButton = null;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (
            AdrenalineConfig.startBeforehandMode() == AdrenalineConfig.StartBeforehandMode.OFF
                || EarlyWorldEntry.isFullChunkReady()
        ) {
            return;
        }
        int diameter = this.progressListener.getDiameter();
        for (int x = 0; x < diameter; x++) {
            for (int z = 0; z < diameter; z++) {
                if (this.progressListener.getStatus(x, z) == ChunkStatus.FULL) {
                    EarlyWorldEntry.markFullChunkReady();
                    return;
                }
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void adrenaline$renderCancelButton(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.adrenaline$cancelButton != null) {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            this.adrenaline$cancelButton.active = server != null && !server.isShutdown() && !WorldLoadCancellation.isRequested();
        }
        if (this.adrenaline$earlyEntryButton != null) {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            this.adrenaline$earlyEntryButton.active = !EarlyWorldEntry.isRequested() && server != null && !server.isShutdown() && !WorldLoadCancellation.isRequested();
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (BuildConfig.DEBUG && AdrenalineConfig.showThreadVisualizer()) {
            this.adrenaline$renderThreadVisualizer(guiGraphics);
        }
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void adrenaline$clearChunkPreviews(CallbackInfo ci) {
        this.adrenaline$releaseChunkPreviewTexture();
        WorldgenChunkPreview.end();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V"))
    private void adrenaline$renderScaledChunkMap(GuiGraphics guiGraphics, StoringChunkProgressListener progressListener, int centerX, int centerY, int cellSize, int padding) {
        if (AprilFoolsEasterEgg.shouldRender()) {
            int mapSize = DEFAULT_DIAMETER * (cellSize + padding) - padding;
            AprilFoolsEasterEgg.render(guiGraphics, centerX, centerY, mapSize);
            return;
        }
        if (!WorldLoadCancellation.isNewWorld() && AdrenalineConfig.fastTerrainLoadingMode() != AdrenalineConfig.FastTerrainLoadingMode.OFF) {
            return;
        }
        if (AdrenalineConfig.resolvedSpawnZoneRadius() == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS) {
            return;
        }

        boolean showChunkPreview = AdrenalineConfig.showChunkPreview();
        int renderCellSize = showChunkPreview ? WorldgenChunkPreview.SIZE : cellSize;
        int renderPadding = showChunkPreview ? 0 : padding;
        int step = renderCellSize + renderPadding;
        int diameter = progressListener.getDiameter();
        int pixels = diameter * step - renderPadding;
        if (pixels <= 0) {
            LevelLoadingScreen.renderChunks(guiGraphics, progressListener, centerX, centerY, renderCellSize, renderPadding);
            this.adrenaline$renderChunkPreviews(guiGraphics, progressListener, centerX, centerY, renderCellSize, renderPadding);
            return;
        }

        float scale;
        if (showChunkPreview) {
            int availableWidth = Math.max(1, this.width - CHUNK_PREVIEW_HORIZONTAL_MARGIN);
            int availableHeight = Math.max(1, this.height - CHUNK_PREVIEW_VERTICAL_MARGIN);
            scale = Math.min(
                1.0F,
                Math.min(
                    (float) availableWidth / (float) pixels,
                    (float) availableHeight / (float) pixels
                )
            );
        } else {
            float basePixels = (float) (DEFAULT_DIAMETER * step - renderPadding);
            scale = basePixels / (float) pixels;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.pose().translate(-centerX, -centerY, 0.0F);
        try {
            LevelLoadingScreen.renderChunks(guiGraphics, progressListener, centerX, centerY, renderCellSize, renderPadding);
            this.adrenaline$renderChunkPreviews(guiGraphics, progressListener, centerX, centerY, renderCellSize, renderPadding);
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    @Unique
    private void adrenaline$renderChunkPreviews(
        GuiGraphics guiGraphics,
        StoringChunkProgressListener progressListener,
        int centerX,
        int centerY,
        int cellSize,
        int padding
    ) {
        if (!AdrenalineConfig.showChunkPreview() || cellSize <= 0) {
            return;
        }

        if (!this.adrenaline$ensureChunkPreviewTexture(progressListener.getDiameter())) {
            return;
        }

        MixinStoringChunkProgressListenerAccessor accessor =
            (MixinStoringChunkProgressListenerAccessor) (Object) progressListener;
        ChunkPos spawnPos = accessor.adrenaline$getSpawnPos();
        if (spawnPos == null) {
            return;
        }

        DynamicTexture texture = this.adrenaline$chunkPreviewTexture;
        NativeImage texturePixels = texture.getPixels();
        if (texturePixels == null) {
            return;
        }

        int diameter = this.adrenaline$chunkPreviewTextureDiameter;
        boolean dirty = false;
        int radius = accessor.adrenaline$getRadius();

        for (int z = 0; z < diameter; z++) {
            for (int x = 0; x < diameter; x++) {
                if (progressListener.getStatus(x, z) != ChunkStatus.FULL) {
                    continue;
                }
                int gridIndex = z * diameter + x;
                if (this.adrenaline$uploadedChunkPreviews[gridIndex]) {
                    continue;
                }
                ChunkPos chunkPos = new ChunkPos(
                    spawnPos.x + x - radius,
                    spawnPos.z + z - radius
                );
                int[] preview = WorldgenChunkPreview.get(chunkPos);
                if (preview == null) {
                    continue;
                }

                int textureX = x * WorldgenChunkPreview.SIZE;
                int textureY = z * WorldgenChunkPreview.SIZE;
                for (int previewZ = 0; previewZ < WorldgenChunkPreview.SIZE; previewZ++) {
                    for (int previewX = 0; previewX < WorldgenChunkPreview.SIZE; previewX++) {
                        texturePixels.setPixelRGBA(
                            textureX + previewX,
                            textureY + previewZ,
                            adrenaline$argbToAbgr(
                                preview[previewZ * WorldgenChunkPreview.SIZE + previewX]
                            )
                        );
                    }
                }
                this.adrenaline$uploadedChunkPreviews[gridIndex] = true;
                dirty = true;
            }
        }

        if (dirty) {
            texture.upload();
        }

        int textureSize = diameter * WorldgenChunkPreview.SIZE;
        guiGraphics.blit(
            this.adrenaline$chunkPreviewTextureLocation,
            centerX - textureSize / 2,
            centerY - textureSize / 2,
            0.0F,
            0.0F,
            textureSize,
            textureSize,
            textureSize,
            textureSize
        );
    }

    @Unique
    private boolean adrenaline$ensureChunkPreviewTexture(int diameter) {
        if (
            this.adrenaline$chunkPreviewTexture != null
                && this.adrenaline$chunkPreviewTextureDiameter == diameter
        ) {
            return true;
        }

        this.adrenaline$releaseChunkPreviewTexture();

        int textureSize = diameter * WorldgenChunkPreview.SIZE;
        DynamicTexture texture = new DynamicTexture(textureSize, textureSize, true);
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            texture.close();
            return false;
        }
        pixels.fillRect(0, 0, textureSize, textureSize, 0);

        Minecraft minecraft = Minecraft.getInstance();
        this.adrenaline$chunkPreviewTexture = texture;
        this.adrenaline$chunkPreviewTextureLocation = minecraft.getTextureManager().register(
            "adrenaline_worldgen_chunk_preview",
            texture
        );
        this.adrenaline$chunkPreviewTextureDiameter = diameter;
        this.adrenaline$uploadedChunkPreviews = new boolean[diameter * diameter];
        texture.upload();
        return true;
    }

    @Unique
    private void adrenaline$releaseChunkPreviewTexture() {
        if (this.adrenaline$chunkPreviewTextureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(
                this.adrenaline$chunkPreviewTextureLocation
            );
        } else if (this.adrenaline$chunkPreviewTexture != null) {
            this.adrenaline$chunkPreviewTexture.close();
        }
        this.adrenaline$chunkPreviewTexture = null;
        this.adrenaline$chunkPreviewTextureLocation = null;
        this.adrenaline$chunkPreviewTextureDiameter = 0;
        this.adrenaline$uploadedChunkPreviews = null;
    }

    @Unique
    private static int adrenaline$argbToAbgr(int color) {
        return color & 0xFF00FF00
            | (color & 0x00FF0000) >>> 16
            | (color & 0x000000FF) << 16;
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;getFormattedProgress()Ljava/lang/String;"))
    private String adrenaline$replaceInstantProgressText(LevelLoadingScreen screen) {
        if (!WorldLoadCancellation.isNewWorld() && AdrenalineConfig.fastTerrainLoadingMode() != AdrenalineConfig.FastTerrainLoadingMode.OFF) {
            return Component.translatable("gui.adrenaline.loading.getting_spawnpoint").getString();
        }
        return AdrenalineConfig.resolvedSpawnZoneRadius() == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? Component.translatable("gui.adrenaline.loading.searching_spawnpoint").getString() : this.getFormattedProgress();
    }

    @Unique
    private void adrenaline$renderThreadVisualizer(GuiGraphics guiGraphics) {
        int[] states = ChunkJobScheduler.get().workerSnapshot();
        int rows = (states.length + 1) / 2;
        int step = Math.max(3, Math.min(10, (this.height - 16) / Math.max(1, rows)));
        int size = Math.max(2, step - 2);
        int startX = this.width - step * 2 - 4;
        int startY = (this.height - rows * step) / 2;
        for (int i = 0; i < states.length; i++) {
            int color = switch (states[i]) {
                case ChunkJobScheduler.WORKER_ACTIVE -> 0xFF55FF55;
                case ChunkJobScheduler.WORKER_WAITING -> 0xFFFFFF55;
                case ChunkJobScheduler.WORKER_IDLE -> 0xFFFF5555;
                default -> 0xFF555555;
            };
            int x = startX + (i & 1) * step;
            int y = startY + (i >> 1) * step;
            guiGraphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
            guiGraphics.fill(x, y, x + size, y + size, color);
        }
    }

    @Unique
    private void adrenaline$cancelWorldCreation(Button button) {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || server.isShutdown()) {
            return;
        }
        GlobalCommon.LOGGER.info("World creation cancellation requested");
        WorldLoadCancellation.request();
        ChunkJobScheduler.get().cancel(server);
        button.active = false;
        button.setMessage(Component.translatable("gui.adrenaline.loading.cancelling_world_creation"));
        server.halt(false);
        String levelToDelete = WorldLoadCancellation.levelToDelete();
        WorldLoadCancellation.markClientHandled();
        minecraft.clearLevel(new TitleScreen());
        if (levelToDelete != null) {
            WorldDeletion.deleteAsync(minecraft, server, levelToDelete);
        }
    }

    @Unique
    private void adrenaline$requestEarlyEntry(Button button) {
        EarlyWorldEntry.request();
        button.active = false;
    }

}
