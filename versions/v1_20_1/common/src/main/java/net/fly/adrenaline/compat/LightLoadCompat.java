package net.fly.adrenaline.compat;

import net.fly.adrenaline.GlobalCommon;

public final class LightLoadCompat {
    private LightLoadCompat() {
    }

    public static void disable() {
        if (!Boolean.getBoolean("lightload.disable")) {
            System.setProperty("lightload.disable", "true");
            GlobalCommon.LOGGER.warn("Disabled LightLoad mixins and startup optimizations for this launch");
        }
    }
}
