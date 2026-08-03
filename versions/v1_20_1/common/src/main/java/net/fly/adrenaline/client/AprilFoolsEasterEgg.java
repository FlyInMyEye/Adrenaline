package net.fly.adrenaline.client;

import java.time.LocalDate;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.util.WorldLoadCancellation;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class AprilFoolsEasterEgg {

    private static final int FRAME_SIZE = 128;
    private static final ResourceLocation[] FRAMES = {
        frame(0),
        frame(1),
        frame(2),
        frame(3),
        frame(4),
        frame(5)
    };

    private AprilFoolsEasterEgg() {
    }

    public static boolean shouldRender() {
        if (!WorldLoadCancellation.isNewWorld()) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return today.getMonthValue() == 4 && today.getDayOfMonth() == 1 || BuildConfig.DEBUG && AdrenalineConfig.forceEasterEgg();
    }

    public static void render(GuiGraphics guiGraphics, int centerX, int centerY, int size) {
        ResourceLocation texture = FRAMES[(int) (Util.getMillis() / 50L % FRAMES.length)];
        float scale = (float) size / FRAME_SIZE;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX, centerY, 0.0F);
        guiGraphics.pose().scale(scale, scale, 1.0F);
        try {
            guiGraphics.blit(texture, -FRAME_SIZE / 2, -FRAME_SIZE / 2, 0, 0, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE);
        } finally {
            guiGraphics.pose().popPose();
        }
    }

    private static ResourceLocation frame(int index) {
        return new ResourceLocation("adrenaline", "textures/gui/april_fools_" + index + ".png");
    }
}
