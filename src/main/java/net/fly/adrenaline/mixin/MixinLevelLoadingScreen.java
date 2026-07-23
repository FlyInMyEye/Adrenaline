package net.fly.adrenaline.mixin;

import net.fly.adrenaline.Adrenaline;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.scheduler.ChunkJobScheduler;
import net.fly.adrenaline.util.WorldLoadCancellation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class MixinLevelLoadingScreen extends Screen {

    private static final int DEFAULT_SPAWN_ZONE_RADIUS = 11;
    private static final int DEFAULT_DIAMETER = (DEFAULT_SPAWN_ZONE_RADIUS + ChunkStatus.maxDistance()) * 2 + 1;

    @Shadow
    private String getFormattedProgress() {
        return null;
    }

    @Unique
    private Button adrenaline$cancelButton;

    protected MixinLevelLoadingScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        this.adrenaline$cancelButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.adrenaline.cancel"), this::adrenaline$cancelWorldCreation).bounds((this.width - 100) / 2, this.height - 28, 100, 20).build());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void adrenaline$renderCancelButton(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        this.adrenaline$cancelButton.active = server != null && !server.isShutdown() && !WorldLoadCancellation.isRequested();
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
        return AdrenalineConfig.resolvedSpawnZoneRadius() == AdrenalineConfig.MIN_SPAWN_ZONE_RADIUS ? Component.translatable("gui.adrenaline.loading.searching_spawnpoint").getString() : this.getFormattedProgress();
    }

    @Unique
    private void adrenaline$cancelWorldCreation(Button button) {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || server.isShutdown()) {
            return;
        }
        Adrenaline.LOGGER.info("World creation cancellation requested");
        WorldLoadCancellation.request();
        ChunkJobScheduler.get().cancel(server);
        button.active = false;
        button.setMessage(Component.translatable("gui.adrenaline.loading.cancelling_world_creation"));
        server.halt(false);
    }

}
