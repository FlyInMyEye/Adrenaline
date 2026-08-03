package net.fly.adrenaline.mixin;

import java.util.function.Function;
import net.fly.adrenaline.client.BackgroundWorldSave;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldOpenFlows.class)
public class MixinWorldOpenFlows {

    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void adrenaline$awaitSaveBeforeLoad(Screen screen, String levelId, CallbackInfo ci) {
        BackgroundWorldSave.awaitCompletion();
    }

    @Inject(method = "createFreshLevel", at = @At("HEAD"))
    private void adrenaline$awaitSaveBeforeCreate(String levelId, LevelSettings settings, WorldOptions options, Function<RegistryAccess, WorldDimensions> dimensions, CallbackInfo ci) {
        BackgroundWorldSave.awaitCompletion();
    }

    @Inject(method = "createLevelFromExistingSettings", at = @At("HEAD"))
    private void adrenaline$awaitSaveBeforeRecreate(LevelStorageSource.LevelStorageAccess storageAccess, ReloadableServerResources resources, LayeredRegistryAccess<RegistryLayer> registries, WorldData worldData, CallbackInfo ci) {
        BackgroundWorldSave.awaitCompletion();
    }
}
