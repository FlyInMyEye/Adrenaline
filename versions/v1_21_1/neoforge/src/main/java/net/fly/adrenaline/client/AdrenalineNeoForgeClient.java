package net.fly.adrenaline.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.internal.BrandingControl;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = "adrenaline", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AdrenalineNeoForgeClient {
    private AdrenalineNeoForgeClient() {
    }

    @SubscribeEvent
    public static void initialize(FMLClientSetupEvent event) {
        ModList.get().getModContainerById("adrenaline").orElseThrow().registerExtensionPoint(
            IConfigScreenFactory.class,
            (container, parent) -> AdrenalineConfigScreen.create(parent));
        NeoForge.EVENT_BUS.addListener(AdrenalineNeoForgeClient::positionWorldLoadStatus);
    }

    private static void positionWorldLoadStatus(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }
        int[] offset = {20};
        BrandingControl.forEachLine(true, false, (line, text) -> offset[0] = (line + 2) * (Minecraft.getInstance().font.lineHeight + 1));
        for (var child : screen.children()) {
            if (child instanceof WorldLoadStatusWidget status) {
                status.setY(screen.height - offset[0]);
            }
        }
    }
}
