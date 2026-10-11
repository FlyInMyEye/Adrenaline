package net.fly.adrenaline.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface MixinMinecraftFrameInvoker {

    @Invoker("runTick")
    void adrenaline$runTick(boolean renderLevel);
}
