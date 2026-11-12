package net.fly.adrenaline.client;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.client.ConfigScreenHandler;
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
    }
}
