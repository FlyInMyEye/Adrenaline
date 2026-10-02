package net.fly.adrenaline.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.WorldLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(WorldLoader.class)
public class MixinWorldLoader {

    @Unique
    private static final Object adrenaline$registryLoadLock = new Object();

    @WrapMethod(method = "load")
    private static <D, R> CompletableFuture<R> adrenaline$serializeRegistryLoading(
        WorldLoader.InitConfig config,
        WorldLoader.WorldDataSupplier<D> supplier,
        WorldLoader.ResultFactory<D, R> factory,
        Executor backgroundExecutor,
        Executor gameExecutor,
        Operation<CompletableFuture<R>> original
    ) {
        synchronized (adrenaline$registryLoadLock) {
            return original.call(config, supplier, factory, backgroundExecutor, gameExecutor);
        }
    }
}
