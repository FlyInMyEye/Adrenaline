package net.fly.adrenaline.compat;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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

    private static final Map<String, Map<MethodKey, Map<MethodKey, Set<OptimizationTakeoverRegistry.Optimization>>>> LOCAL_CLAIMS = new HashMap<>();

    private OptimizationTakeoverDetector() {
    }

    public static synchronized void captureClaims(ClassNode targetClass, IMixinInfo mixinInfo) {
        Set<OptimizationTakeoverRegistry.Optimization> classControl = controlledBy(mixinInfo.getClassNode(0).visibleAnnotations, mixinInfo.getClassNode(0).invisibleAnnotations);
        Map<String, Set<OptimizationTakeoverRegistry.Optimization>> controlledMethods = new HashMap<>();
        Set<String> localMethods = new HashSet<>();
        for (MethodNode method : targetClass.methods) {
            Set<OptimizationTakeoverRegistry.Optimization> methodControl = controlledBy(method.visibleAnnotations, method.invisibleAnnotations);
            if (Boolean.TRUE.equals(annotationObject(method.visibleAnnotations, CONTROL_DESCRIPTOR, "local"))
                || Boolean.TRUE.equals(annotationObject(method.invisibleAnnotations, CONTROL_DESCRIPTOR, "local"))) {
                localMethods.add(method.name + method.desc);
            }
            if (!methodControl.isEmpty()) {
                controlledMethods.computeIfAbsent(method.name + method.desc, key -> EnumSet.noneOf(OptimizationTakeoverRegistry.Optimization.class)).addAll(methodControl);
            }
            if (!classControl.isEmpty() && mixinInfo.getClassName().equals(mergedBy(method))) {
                controlledMethods.computeIfAbsent(method.name + method.desc, key -> EnumSet.noneOf(OptimizationTakeoverRegistry.Optimization.class)).addAll(classControl);
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
                if (controls != null && localMethods.contains(call.name + call.desc)) {
                    LOCAL_CLAIMS.computeIfAbsent(targetClass.name, key -> new HashMap<>())
                        .computeIfAbsent(new MethodKey(method.name, method.desc), key -> new HashMap<>())
                        .computeIfAbsent(new MethodKey(call.name, call.desc), key -> EnumSet.noneOf(OptimizationTakeoverRegistry.Optimization.class)).addAll(controls);
                } else if (controls != null) {
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

        Map<MethodKey, Map<MethodKey, Set<OptimizationTakeoverRegistry.Optimization>>> localClaims = LOCAL_CLAIMS.get(targetClass.name);
        if (localClaims != null) {
            for (var callerEntry : localClaims.entrySet()) {
                MethodKey callerKey = callerEntry.getKey();
                MethodNode caller = methods.get(callerKey.name + callerKey.desc);
                if (caller == null) continue;
                String methodOwner = externalOwner(caller);
                if (methodOwner == null) {
                    for (AbstractInsnNode instruction : caller.instructions) {
                        if (instruction instanceof MethodInsnNode call && targetClass.name.equals(call.owner)) {
                            MethodNode dependency = methods.get(call.name + call.desc);
                            if (dependency != null && externalOwner(dependency) != null
                                && (cancelsCallback(dependency) || dependency.name.toLowerCase(java.util.Locale.ROOT).contains("wrapmethod"))) {
                                methodOwner = externalOwner(dependency);
                                break;
                            }
                        }
                    }
                }
                for (var handlerEntry : callerEntry.getValue().entrySet()) {
                    MethodKey handlerKey = handlerEntry.getKey();
                    MethodNode handler = methods.get(handlerKey.name + handlerKey.desc);
                    String controller = methodOwner;
                    if (controller == null && handler != null) {
                        controller = externalDependency(targetClass.name, handler, methods, new HashSet<>(), false);
                    }
                    if (controller != null) {
                        String displayName = displayNameResolver.apply(controller);
                        for (var optimization : handlerEntry.getValue()) OptimizationTakeoverRegistry.record(optimization, displayName);
                    }
                }
            }
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
                boolean followVanilla = claim.getValue().contains(OptimizationTakeoverRegistry.Optimization.BIOME_FIDDLE)
                    || claim.getValue().contains(OptimizationTakeoverRegistry.Optimization.SURFACE_DEPTH_NOISE)
                    || claim.getValue().contains(OptimizationTakeoverRegistry.Optimization.SURFACE_BAND_NOISE);
                controller = externalDependency(targetClass.name, targetMethod, methods, new HashSet<>(), followVanilla);
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
                normalizedName.startsWith("constant$")
                        || normalizedName.startsWith("localvar$")
                        || normalizedName.startsWith("args$")
                        || normalizedName.contains("redirect$")
                        || normalizedName.contains("wrap")
                        || normalizedName.contains("modify")
                        || normalizedName.contains("$redirect")
                        || normalizedName.contains("$wrap")
                        || normalizedName.contains("$modify")
        );
    }

    private static String externalDependency(String owner, MethodNode method, Map<String, MethodNode> methods, Set<String> visited, boolean followVanilla) {
        if (!visited.add(method.name + method.desc)) {
            return null;
        }
        String controller = externalOwner(method);
        if (controller != null) {
            return controller;
        }
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode call) || !owner.equals(call.owner)) {
                continue;
            }
            MethodNode dependency = methods.get(call.name + call.desc);
            if (dependency == null) {
                continue;
            }
            String external = externalOwner(dependency);
            if (external != null) {
                if (isDestructiveHandler(dependency.name) || cancelsCallback(dependency)) {
                    return external;
                }
            } else if (followVanilla || mergedBy(dependency) != null || dependency.name.startsWith("mixinextras$")) {
                controller = externalDependency(owner, dependency, methods, visited, followVanilla);
                if (controller != null) {
                    return controller;
                }
            }
        }
        return null;
    }

    private static String externalOwner(MethodNode method) {
        String owner = mergedBy(method);
        return owner != null && !owner.startsWith(ADRENALINE_MIXIN_PACKAGE) ? owner : null;
    }

    private static String mergedBy(MethodNode method) {
        String owner = annotationValue(method.visibleAnnotations, MIXIN_MERGED_DESCRIPTOR, "mixin");
        return owner != null ? owner.replace('/', '.') : normalized(annotationValue(method.invisibleAnnotations, MIXIN_MERGED_DESCRIPTOR, "mixin"));
    }

    private static Set<OptimizationTakeoverRegistry.Optimization> controlledBy(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        Object value = annotationObject(visible, CONTROL_DESCRIPTOR, "value");
        if (value == null) {
            value = annotationObject(invisible, CONTROL_DESCRIPTOR, "value");
        }
        Set<OptimizationTakeoverRegistry.Optimization> controls = EnumSet.noneOf(OptimizationTakeoverRegistry.Optimization.class);
        List<?> entries = value instanceof List<?> list ? list : value == null ? List.of() : java.util.Collections.singletonList(value);
        for (Object entry : entries) {
            if (entry instanceof String[] enumValue && enumValue.length == 2) {
                controls.add(OptimizationTakeoverRegistry.Optimization.valueOf(enumValue[1]));
            }
        }
        return controls;
    }

    private static boolean cancelsCallback(MethodNode method) {
        boolean cancels = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.JumpInsnNode
                || instruction instanceof org.objectweb.asm.tree.TableSwitchInsnNode
                || instruction instanceof org.objectweb.asm.tree.LookupSwitchInsnNode) {
                return false;
            }
            if (instruction instanceof MethodInsnNode call && call.owner.startsWith("org/spongepowered/asm/mixin/injection/callback/")
                && (call.name.equals("cancel") || call.name.equals("setReturnValue"))) {
                cancels = true;
            }
        }
        return cancels;
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
