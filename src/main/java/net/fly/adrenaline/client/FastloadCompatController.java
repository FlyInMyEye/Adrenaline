package net.fly.adrenaline.client;

import net.fly.adrenaline.compatdata.ICrashController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class FastloadCompatController implements ICrashController {

    private boolean shouldShow;

    private boolean shown;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.shown || !this.shouldShow) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
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
