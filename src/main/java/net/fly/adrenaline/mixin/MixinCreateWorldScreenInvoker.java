package net.fly.adrenaline.mixin;

import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreateWorldScreen.class)
public interface MixinCreateWorldScreenInvoker {

    @Invoker("<init>")
    static CreateWorldScreen adrenaline$create(Minecraft minecraft, Screen parent, WorldCreationContext context, Optional<ResourceKey<WorldPreset>> preset, OptionalLong seed) {
        throw new AssertionError();
    }
}
