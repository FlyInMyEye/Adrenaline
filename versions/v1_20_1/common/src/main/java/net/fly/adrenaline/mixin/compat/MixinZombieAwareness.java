package net.fly.adrenaline.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;

@Pseudo
@Mixin(targets = "com.corosus.zombieawareness.ZombieAwareness", remap = false)
public abstract class MixinZombieAwareness {

    @Shadow(remap = false) public static HashMap<UUID, CompoundTag> entityData;

    @WrapMethod(method = "getPersistentData", remap = false)
    private CompoundTag adrenaline$readEntityData(Entity entity, Operation<CompoundTag> original) {
        synchronized (entityData) {
            return original.call(entity);
        }
    }

    @WrapMethod(method = "setPersistentData", remap = false)
    private CompoundTag adrenaline$writeEntityData(Entity entity, CompoundTag data, Operation<CompoundTag> original) {
        synchronized (entityData) {
            return original.call(entity, data);
        }
    }
}
