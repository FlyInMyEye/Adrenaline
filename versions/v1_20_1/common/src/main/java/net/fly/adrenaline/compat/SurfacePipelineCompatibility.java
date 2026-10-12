package net.fly.adrenaline.compat;

import java.lang.reflect.Method;
import net.fly.adrenaline.worldgen.SurfaceRulesContextFactory;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

public final class SurfacePipelineCompatibility {

    private static volatile boolean originalTraversal;
    public static final int MIXIN_PRIORITY = 1100;
    private static final ClassValue<Hooks> FOREIGN_HOOKS = new ClassValue<>() {
        @Override
        protected Hooks computeValue(Class<?> type) {
            boolean foreign = false;
            boolean capturesContext = false;
            int priority = Integer.MIN_VALUE;
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                for (Method method : current.getDeclaredMethods()) {
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    if (merged != null && !merged.mixin().startsWith("net.fly.adrenaline.mixin.")) {
                        foreign = true;
                        priority = Math.max(priority, merged.priority());
                        for (Class<?> parameter : method.getParameterTypes()) {
                            capturesContext |= parameter.isAssignableFrom(SurfaceRulesContextFactory.contextType());
                        }
                    }
                }
            }
            return new Hooks(foreign, capturesContext, priority);
        }
    };

    private SurfacePipelineCompatibility() {
    }

    public static void preserveOriginalTraversal() {
        originalTraversal = true;
    }

    public static boolean usesOriginalTraversal() {
        return originalTraversal;
    }

    public static boolean usesOriginalTraversal(SurfaceSystem system) {
        if (FOREIGN_HOOKS.get(system.getClass()).foreign) {
            originalTraversal = true;
        }
        return originalTraversal;
    }

    public static boolean hasForeignHooks(Class<?> type) {
        return FOREIGN_HOOKS.get(type).foreign;
    }

    public static boolean canReuseContext(SurfaceSystem system) {
        Hooks hooks = FOREIGN_HOOKS.get(system.getClass());
        return !hooks.capturesContext && hooks.priority < MIXIN_PRIORITY;
    }

    private record Hooks(boolean foreign, boolean capturesContext, int priority) { }
}
