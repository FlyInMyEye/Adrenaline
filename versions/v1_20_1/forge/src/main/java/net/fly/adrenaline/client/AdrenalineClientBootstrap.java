package net.fly.adrenaline.client;

import net.fly.adrenaline.Adrenaline;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Adrenaline.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AdrenalineClientBootstrap {
    private AdrenalineClientBootstrap() {
    }

    @SubscribeEvent
    public static void initialize(FMLClientSetupEvent event) {
        AdrenalineClient.initialize();
    }
}
