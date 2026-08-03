package net.fly.adrenaline.mixin;

import java.nio.file.Path;
import net.fly.adrenaline.client.BackgroundWorldSave;
import net.minecraft.util.DirectoryLock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DirectoryLock.class)
public class MixinDirectoryLock {

    @Inject(method = "isLocked", at = @At("HEAD"), cancellable = true)
    private static void adrenaline$markSavingWorldLocked(Path path, CallbackInfoReturnable<Boolean> cir) {
        if (BackgroundWorldSave.isSaving(path)) {
            cir.setReturnValue(true);
        }
    }
}
