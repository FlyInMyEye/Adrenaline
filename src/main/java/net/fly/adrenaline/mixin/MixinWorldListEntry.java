package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fly.adrenaline.client.BackgroundWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldSelectionList.WorldListEntry.class)
public class MixinWorldListEntry {

    @Shadow
    @Final
    private LevelSummary summary;

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelSummary;getLevelId()Ljava/lang/String;"))
    private String adrenaline$showSavingInsteadOfLevelId(LevelSummary summary) {
        return BackgroundWorldSave.isSaving(summary.getLevelId()) ? Component.translatable("gui.adrenaline.world.saving").getString() : summary.getLevelId();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelSummary;getLastPlayed()J"))
    private long adrenaline$hideSavingWorldDate(LevelSummary summary) {
        return BackgroundWorldSave.isSaving(summary.getLevelId()) ? -1L : summary.getLastPlayed();
    }

    @WrapOperation(method = {"render", "getNarration"}, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screens/worldselection/WorldSelectionList;WORLD_LOCKED_TOOLTIP:Lnet/minecraft/network/chat/Component;"))
    private Component adrenaline$replaceLockedTooltip(Operation<Component> original) {
        return BackgroundWorldSave.isSaving(this.summary.getLevelId()) ? Component.translatable("gui.adrenaline.world.saving") : original.call();
    }
}
