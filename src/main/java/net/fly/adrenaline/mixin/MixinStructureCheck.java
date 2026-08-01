package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StructureCheck.class)
public class MixinStructureCheck {

    @Unique
    private ReentrantLock adrenaline$cacheLock;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void adrenaline$initializeCacheLock(CallbackInfo ci) {
        this.adrenaline$cacheLock = new ReentrantLock();
    }

    @WrapMethod(method = "checkStart")
    private StructureCheckResult adrenaline$lockCheckStart(ChunkPos pos, Structure structure, boolean skipKnownStructures, Operation<StructureCheckResult> original) {
        this.adrenaline$cacheLock.lock();
        try {
            return original.call(pos, structure, skipKnownStructures);
        } finally {
            this.adrenaline$cacheLock.unlock();
        }
    }

    @WrapMethod(method = "incrementReference")
    private void adrenaline$lockIncrementReference(ChunkPos pos, Structure structure, Operation<Void> original) {
        this.adrenaline$cacheLock.lock();
        try {
            original.call(pos, structure);
        } finally {
            this.adrenaline$cacheLock.unlock();
        }
    }

    @WrapMethod(method = "onStructureLoad")
    private void adrenaline$lockOnStructureLoad(ChunkPos pos, Map<Structure, StructureStart> references, Operation<Void> original) {
        this.adrenaline$cacheLock.lock();
        try {
            original.call(pos, references);
        } finally {
            this.adrenaline$cacheLock.unlock();
        }
    }
}
