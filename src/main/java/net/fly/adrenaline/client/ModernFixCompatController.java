package net.fly.adrenaline.client;

import net.fly.adrenaline.compatdata.ModernFixCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class ModernFixCompatController {

    private boolean checked;
    private boolean needsFix;
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
            this.needsFix = ModernFixCompat.needsConfigFix();
        }

        if (!this.needsFix || this.shown || minecraft.screen == null || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }

        this.shown = true;
        minecraft.setScreen(new ModernFixCompatScreen());
    }
}
