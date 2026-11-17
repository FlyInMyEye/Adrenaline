package net.fly.adrenaline.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.internal.BrandingControl;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "adrenaline", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AdrenalineForgeClient {
    private AdrenalineForgeClient() {
    }

    @SubscribeEvent
    public static void initialize(FMLClientSetupEvent event) {
        ModList.get().getModContainerById("adrenaline").orElseThrow().registerExtensionPoint(
            ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> AdrenalineConfigScreen.create(parent)));
        MinecraftForge.EVENT_BUS.addListener(AdrenalineForgeClient::positionWorldLoadStatus);
    }

    private static void positionWorldLoadStatus(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) {
            return;
        }
        int[] offset = {20};
        BrandingControl.forEachLine(true, false, (text, line) -> offset[0] = (line + 2) * (Minecraft.getInstance().font.lineHeight + 1));
        for (var child : screen.children()) {
            if (child instanceof WorldLoadStatusWidget status) {
                status.setY(screen.height - offset[0]);
            }
        }
    }
}
