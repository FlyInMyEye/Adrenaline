package net.fly.adrenaline.compat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class OptimizationTakeoverDetector {
    private static final String CONTROL_DESCRIPTOR = "Lnet/fly/adrenaline/compat/ControlsOptimization;";
    private static final String MIXIN_MERGED_DESCRIPTOR = "Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;";
    private static final String ADRENALINE_MIXIN_PACKAGE = "net.fly.adrenaline.mixin.";
    private static final Map<String, Map<MethodKey, Set<OptimizationTakeoverRegistry.Optimization>>> CLAIMS = new HashMap<>();

    private OptimizationTakeoverDetector() {
    }

    public static synchronized void captureClaims(ClassNode targetClass, IMixinInfo mixinInfo) {
        OptimizationTakeoverRegistry.Optimization classControl = controlledBy(mixinInfo.getClassNode(0).visibleAnnotations, mixinInfo.getClassNode(0).invisibleAnnotations);
        Map<String, Set<OptimizationTakeoverRegistry.Optimization>> controlledMethods = new HashMap<>();
        for (MethodNode method : targetClass.methods) {
            OptimizationTakeoverRegistry.Optimization methodControl = controlledBy(method.visibleAnnotations, method.invisibleAnnotations);
            if (methodControl != null) {
                controlledMethods.computeIfAbsent(method.name + method.desc, key -> EnumSet.noneOf(OptimizationTakeoverRegistry.Optimization.class)).add(methodControl);
            }
            if (classControl != null && mixinInfo.getClassName().equals(mergedBy(method))) {
                controlledMethods.computeIfAbsent(method.name + method.desc, key -> EnumSet.noneOf(OptimizationTakeoverRegistry.Optimization.class)).add(classControl);
            }
        }
        if (controlledMethods.isEmpty()) {
            return;
        }
        Map<MethodKey, Set<OptimizationTakeoverRegistry.Optimization>> targetClaims = CLAIMS.computeIfAbsent(targetClass.name, key -> new HashMap<>());
        for (MethodNode method : targetClass.methods) {
            Set<OptimizationTakeoverRegistry.Optimization> direct = controlledMethods.get(method.name + method.desc);
            if (direct != null && !method.name.contains("$")) {
                targetClaims.computeIfAbsent(new MethodKey(method.name, method.desc), key -> EnumSet.noneOf(OptimizationTakeoverRegistry.Optimization.class)).addAll(direct);
            }
            for (AbstractInsnNode instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || !targetClass.name.equals(call.owner)) {
                    continue;
                }
                Set<OptimizationTakeoverRegistry.Optimization> controls = controlledMethods.get(call.name + call.desc);
                if (controls != null) {
                    targetClaims.computeIfAbsent(new MethodKey(method.name, method.desc), key -> EnumSet.noneOf(OptimizationTakeoverRegistry.Optimization.class)).addAll(controls);
                }
            }
        }
    }

    public static synchronized void detectTakeovers(
            ClassNode targetClass,
            Function<String, String> displayNameResolver
    ) {
        Map<MethodKey, Set<OptimizationTakeoverRegistry.Optimization>> targetClaims =
                CLAIMS.get(targetClass.name);

        if (targetClaims == null) {
            return;
        }

        Map<String, MethodNode> methods = new HashMap<>();
        for (MethodNode method : targetClass.methods) {
            methods.put(method.name + method.desc, method);
        }

        for (Map.Entry<
                MethodKey,
                Set<OptimizationTakeoverRegistry.Optimization>
                > claim : targetClaims.entrySet()) {
            MethodKey claimedMethod = claim.getKey();
            MethodNode targetMethod =
                    methods.get(claimedMethod.name + claimedMethod.desc);

            if (targetMethod == null) {
                continue;
            }

            String controller = externalOwner(targetMethod);

            if (controller == null) {
                for (AbstractInsnNode instruction : targetMethod.instructions) {
                    if (!(instruction instanceof MethodInsnNode call)) {
                        continue;
                    }

                    if (!targetClass.name.equals(call.owner)) {
                        continue;
                    }

                    MethodNode handler = methods.get(call.name + call.desc);
                    boolean destructive =
                            handler != null
                                    && isDestructiveHandler(handler.name);
                    String externalHandlerOwner =
                            handler == null ? null : externalOwner(handler);

                    if (destructive) {
                        controller = externalHandlerOwner;
                        if (controller != null) {
                            break;
                        }
                    }
                }
            }

            if (controller != null) {
                String displayName = displayNameResolver.apply(controller);

                for (OptimizationTakeoverRegistry.Optimization optimization
                        : claim.getValue()) {
                    OptimizationTakeoverRegistry.record(
                            optimization,
                            displayName
                    );
                }
            }
        }
    }

    private static boolean isDestructiveHandler(String name) {
        String normalizedName = name.toLowerCase(java.util.Locale.ROOT);

        return !normalizedName.startsWith("handler$")
                && (
                normalizedName.contains("redirect$")
                        || normalizedName.contains("wrap")
                        || normalizedName.contains("modify")
                        || normalizedName.contains("$redirect")
                        || normalizedName.contains("$wrap")
                        || normalizedName.contains("$modify")
        );
    }

    private static String externalOwner(MethodNode method) {
        String owner = mergedBy(method);
        return owner != null && !owner.startsWith(ADRENALINE_MIXIN_PACKAGE) ? owner : null;
    }

    private static String mergedBy(MethodNode method) {
        String owner = annotationValue(method.visibleAnnotations, MIXIN_MERGED_DESCRIPTOR, "mixin");
        return owner != null ? owner.replace('/', '.') : normalized(annotationValue(method.invisibleAnnotations, MIXIN_MERGED_DESCRIPTOR, "mixin"));
    }

    private static OptimizationTakeoverRegistry.Optimization controlledBy(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        Object value = annotationObject(visible, CONTROL_DESCRIPTOR, "value");
        if (value == null) {
            value = annotationObject(invisible, CONTROL_DESCRIPTOR, "value");
        }
        if (value instanceof String[] enumValue && enumValue.length == 2) {
            return OptimizationTakeoverRegistry.Optimization.valueOf(enumValue[1]);
        }
        return null;
    }

    private static String annotationValue(List<AnnotationNode> annotations, String descriptor, String key) {
        Object value = annotationObject(annotations, descriptor, key);
        return value == null ? null : value.toString();
    }

    private static Object annotationObject(List<AnnotationNode> annotations, String descriptor, String key) {
        if (annotations == null) {
            return null;
        }
        for (AnnotationNode annotation : annotations) {
            if (!descriptor.equals(annotation.desc) || annotation.values == null) {
                continue;
            }
            for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
                if (key.equals(annotation.values.get(index))) {
                    return annotation.values.get(index + 1);
                }
            }
        }
        return null;
    }

    private static String normalized(String value) {
        return value == null ? null : value.replace('/', '.');
    }

    private record MethodKey(String name, String desc) {
    }
}
