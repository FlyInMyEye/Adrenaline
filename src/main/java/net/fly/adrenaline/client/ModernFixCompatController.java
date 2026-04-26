package net.fly.adrenaline.client;

import net.fly.adrenaline.compatdata.ICrashController;
import net.fly.adrenaline.compatdata.ModernFixCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ModernFixCompatController implements ICrashController {

    private boolean shouldShow;

    private boolean checked;
    private boolean shown;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        if (!this.checked) {
            this.checked = true;
            this.shouldShow = ModernFixCompat.needsConfigFix();
        }

        if (!this.shouldShow || this.shown || minecraft.screen == null || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }

        this.shown = true;
        minecraft.setScreen(new ModernFixCompatScreen());
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
