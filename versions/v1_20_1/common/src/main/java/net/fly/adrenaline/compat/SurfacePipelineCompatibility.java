package net.fly.adrenaline.compat;

import java.lang.reflect.Method;
import net.fly.adrenaline.worldgen.SurfaceRulesContextFactory;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

public final class SurfacePipelineCompatibility {

    public static final int MIXIN_PRIORITY = 1100;
    private static final ClassValue<Hooks> FOREIGN_HOOKS = new ClassValue<>() {
        @Override
        protected Hooks computeValue(Class<?> type) {
            boolean foreign = false;
            boolean capturesContext = false;
            boolean blockStorage = false;
            boolean blockReads = false;
            int priority = Integer.MIN_VALUE;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    if (merged != null && !merged.mixin().startsWith("net.fly.adrenaline.mixin.")) {
                        foreign = true;
                        priority = Math.max(priority, merged.priority());
                        Class<?> result = method.getReturnType();
                        capturesContext |= result.isAssignableFrom(SurfaceRulesContextFactory.contextType());
                        Class<?>[] parameters = method.getParameterTypes();
                        blockReads |= result == BlockState.class || result == LevelChunkSection.class
                            || result == LevelChunkSection[].class || result == int.class
                                && (parameters.length == 0 || parameters.length == 1 && parameters[0] == int.class);
                        String name = method.getName().toLowerCase(java.util.Locale.ROOT);
                        boolean replacesValue = name.startsWith("redirect$") || name.startsWith("wrapoperation$")
                            || name.startsWith("modifyexpressionvalue$") || name.startsWith("modifyconstant$")
                            || name.startsWith("modifyarg$") || name.startsWith("modifyvariable$");
                        blockReads |= replacesValue && (result == int.class || result == boolean.class);
                        boolean injected = replacesValue || name.startsWith("handler$") || name.startsWith("wrapmethod$")
                            || name.startsWith("wrapwithcondition$");
                        boolean callback = false;
                        boolean scalarCallback = true;
                        for (Class<?> parameter : parameters) {
                            callback |= CallbackInfo.class.isAssignableFrom(parameter);
                            scalarCallback &= parameter.isPrimitive() || CallbackInfo.class.isAssignableFrom(parameter);
                            blockReads |= injected && (BlockPos.class.isAssignableFrom(parameter) || parameter == BlockState.class);
                        }
                        blockReads |= injected && callback && scalarCallback;
                        blockStorage |= result == BlockState.class || result == Heightmap.class || result == LevelChunkSection[].class;
                        for (Class<?> parameter : method.getParameterTypes()) {
                            capturesContext |= parameter.isAssignableFrom(SurfaceRulesContextFactory.contextType());
                            blockStorage |= BlockPos.class.isAssignableFrom(parameter) || parameter == BlockState.class
                                || parameter == Heightmap.Types.class || CallbackInfo.class.isAssignableFrom(parameter);
                        }
                    }
                }
            }
            return new Hooks(foreign, capturesContext, priority, blockStorage, blockReads);
        }
    };

    private SurfacePipelineCompatibility() {
    }

    public static boolean hasForeignHooks(Class<?> type) {
        return FOREIGN_HOOKS.get(type).foreign;
    }

    public static boolean hasBlockStorageHooks(Class<?> type) {
        return FOREIGN_HOOKS.get(type).blockStorage;
    }

    public static boolean hasBlockReadHooks(Class<?> type) {
        return FOREIGN_HOOKS.get(type).blockReads;
    }

    public static boolean canReuseContext(SurfaceSystem system) {
        Hooks hooks = FOREIGN_HOOKS.get(system.getClass());
        return !hooks.capturesContext && hooks.priority < MIXIN_PRIORITY;
    }

    private record Hooks(boolean foreign, boolean capturesContext, int priority, boolean blockStorage, boolean blockReads) { }
}
