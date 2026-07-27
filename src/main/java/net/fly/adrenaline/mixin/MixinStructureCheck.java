package net.fly.adrenaline.mixin;

import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StructureCheck.class)
public class MixinStructureCheck {

    @Unique
    private ReentrantLock adrenaline$cacheLock;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$initializeCacheLock(CallbackInfo ci) {
        this.adrenaline$cacheLock = new ReentrantLock();
    }

    @Inject(method = {"checkStart", "incrementReference", "onStructureLoad"}, at = @At("HEAD"))
    private void adrenaline$lockCaches(CallbackInfo ci) {
        this.adrenaline$cacheLock.lock();
    }

    @Inject(method = {"incrementReference", "onStructureLoad"}, at = @At("RETURN"))
    private void adrenaline$unlockCaches(CallbackInfo ci) {
        this.adrenaline$cacheLock.unlock();
    }

    @Inject(method = "checkStart", at = @At("RETURN"))
    private void adrenaline$unlockCaches(CallbackInfoReturnable<StructureCheckResult> cir) {
        this.adrenaline$cacheLock.unlock();
    }
}
