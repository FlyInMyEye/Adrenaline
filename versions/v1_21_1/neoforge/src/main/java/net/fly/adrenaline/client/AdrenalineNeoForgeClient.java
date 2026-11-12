package net.fly.adrenaline.client;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
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
    }
}
