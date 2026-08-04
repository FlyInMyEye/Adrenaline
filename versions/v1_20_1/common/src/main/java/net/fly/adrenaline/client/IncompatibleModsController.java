package net.fly.adrenaline.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

public final class IncompatibleModsController {
    private List<String> incompatibleMods = List.of();
    private boolean shown;

    public void tick(Minecraft minecraft) {
        if (this.shown || this.incompatibleMods.isEmpty()) {
            return;
        }
        if (minecraft == null || !(minecraft.screen instanceof TitleScreen)) {
            return;
        }
        this.shown = true;
        minecraft.setScreen(new IncompatibleModsScreen(this.incompatibleMods));
    }

    public void setIncompatibleMods(List<String> incompatibleMods) {
        this.incompatibleMods = List.copyOf(incompatibleMods);
    }
}
