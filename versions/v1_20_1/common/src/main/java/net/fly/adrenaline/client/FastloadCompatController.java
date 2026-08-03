package net.fly.adrenaline.client;

import net.fly.adrenaline.compatdata.ICrashController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

public final class FastloadCompatController implements ICrashController {

    private boolean shouldShow;

    private boolean shown;

    public void tick(Minecraft minecraft) {
        if (this.shown || !this.shouldShow) {
            return;
        }

        if (minecraft == null || minecraft.screen == null || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }

        this.shown = true;
        minecraft.setScreen(new FastloadCompatScreen());
    }

    @Override
    public boolean shouldShow() {
        return this.shouldShow;
    }

    @Override
    public void setShouldShow(boolean shouldShow) {
        this.shouldShow = shouldShow;
    }
}
