package net.fly.adrenaline.mixin;

import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.util.WorldgenDifferenceState;
import net.minecraft.network.Connection;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public class MixinPlayerList {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void adrenaline$giveWorldgenDifferenceTool(Connection connection, ServerPlayer player, CommonListenerCookie cookie, CallbackInfo ci) {
        if (!BuildConfig.DEBUG || !WorldgenDifferenceState.isActive(player.getServer())) {
            return;
        }

        ItemStack tool = new ItemStack(Items.STICK);
        tool.set(DataComponents.CUSTOM_NAME, Component.literal("Adrenaline"));
        if (!player.getInventory().add(tool)) {
            player.drop(tool, false);
        }
    }
}
