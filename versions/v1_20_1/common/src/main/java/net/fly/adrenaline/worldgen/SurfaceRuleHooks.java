package net.fly.adrenaline.worldgen;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

public final class SurfaceRuleHooks {

    private static volatile Set<String> hooks;
    private static final Map<String, Set<String>> CALL_HOOKS = new ConcurrentHashMap<>();
    private static final Map<String, Map<String, String>> FORWARDERS = new ConcurrentHashMap<>();

    private static final Map<String, Set<String>> SEQUENCES = new ConcurrentHashMap<>();
    private static final ClassValue<Boolean> RULE_HOOKS = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return ruleHooks(type);
        }
    };

    public static boolean hasRuleHooks(Class<?> type) {
        return RULE_HOOKS.get(type);
    }

    private SurfaceRuleHooks() {
    }

    public static void capture(ClassNode type) {
        SurfaceTemperatureTransforms.capture(type);
        Set<String> sequences = new HashSet<>();
        for (MethodNode method : type.methods) {
            if ((foreign(method.visibleAnnotations) || foreign(method.invisibleAnnotations)) && sequence(type, method)) {
                sequences.add(method.name + method.desc);
            }
        }
        SEQUENCES.put(type.name.replace('/', '.'), Set.copyOf(sequences));
        Map<String, String> forwarders = new java.util.HashMap<>();
        for (MethodNode method : type.methods) {
            if (!(foreign(method.visibleAnnotations) || foreign(method.invisibleAnnotations))
                || !method.desc.equals("()Z") || (method.access & Opcodes.ACC_PUBLIC) == 0) {
                continue;
            }
            var code = new java.util.ArrayList<org.objectweb.asm.tree.AbstractInsnNode>();
            for (var instruction : method.instructions) {
                if (instruction.getOpcode() >= 0) code.add(instruction);
            }
            if (code.size() == 3 && code.get(0) instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == 0
                && code.get(1) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && call.desc.equals("()Z") && (call.owner.equals(type.name) || call.owner.equals(type.superName))
                && code.get(2).getOpcode() == Opcodes.IRETURN) {
                forwarders.put(method.name, call.name);
            }
        }
        FORWARDERS.put(type.name.replace('/', '.'), Map.copyOf(forwarders));
        boolean context = type.methods.stream().anyMatch(method -> method.name.equals("adrenaline$getMinSurfaceLevel"));
        Set<String> foreign = new HashSet<>();
        for (MethodNode method : type.methods) {
            if (foreign(method.visibleAnnotations) || foreign(method.invisibleAnnotations)) {
                foreign.add(method.name + method.desc);
            }
        }
        boolean changed;
        do {
            changed = false;
            for (MethodNode method : type.methods) {
                String key = method.name + method.desc;
                if (foreign.contains(key)) {
                    continue;
                }
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(type.name) && foreign.contains(call.name + call.desc)) {
                        changed |= foreign.add(key);
                        break;
                    }
                    if (instruction instanceof InvokeDynamicInsnNode call) {
                        for (Object argument : call.bsmArgs) {
                            if (argument instanceof Handle handle && handle.getOwner().equals(type.name) && foreign.contains(handle.getName() + handle.getDesc())) {
                                changed |= foreign.add(key);
                            }
                        }
                    }
                }
            }
        } while (changed);
        CALL_HOOKS.put(type.name.replace('/', '.'), Set.copyOf(foreign));
        if (context) hooks = Set.copyOf(foreign);
    }

    public static boolean hooked(Class<?> type, String method) {
        Set<String> current = CALL_HOOKS.get(type.getName());
        return current == null ? SurfacePipelineCompatibility.hasForeignHooks(type) : current.contains(method);
    }

    public static boolean hasHolderValueHooks(Class<?> type) {
        for (Method method : net.minecraft.core.Holder.class.getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == Object.class) {
                return hooked(type, method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method));
            }
        }
        return true;
    }

    static boolean hasWeightedSelectionHooks(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Set<String> affected = CALL_HOOKS.get(current.getName());
            for (Method method : current.getDeclaredMethods()) {
                if (method.getReturnType() != java.util.Optional.class
                    || !java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{net.minecraft.util.RandomSource.class})) continue;
                if (affected != null) {
                    if (affected.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method))) return true;
                } else {
                    MixinMerged merged = method.getAnnotation(MixinMerged.class);
                    if (merged != null && !merged.mixin().startsWith("net.fly.adrenaline.mixin.")) return true;
                }
            }
        }
        return false;
    }

    public static boolean hooked(String method) {
        Set<String> current = hooks;
        return current == null ? SurfacePipelineCompatibility.hasForeignHooks(SurfaceRulesContextFactory.contextType()) : current.contains(method);
    }

    public static boolean hasHolderHooks(Class<?> type) {
        Set<String> affected = CALL_HOOKS.get(type.getName());
        if (affected == null) return SurfacePipelineCompatibility.hasForeignHooks(type);
        for (Method method : type.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if ((parameters.length == 1 && parameters[0] == java.util.function.Predicate.class && method.getReturnType() == boolean.class
                || parameters.length == 0 && method.getReturnType() == java.util.Optional.class)
                && affected.contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method))) {
                return true;
            }
        }
        return false;
    }

    private static boolean ruleHooks(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                MixinMerged merged = method.getAnnotation(MixinMerged.class);
                if (merged != null && !merged.mixin().startsWith("net.fly.adrenaline.mixin.") && !forwarder(method)
                    && !SEQUENCES.getOrDefault(current.getName(), Set.of()).contains(method.getName() + org.objectweb.asm.Type.getMethodDescriptor(method))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean forwarder(Method method) {
        String target = FORWARDERS.getOrDefault(method.getDeclaringClass().getName(), Map.of()).get(method.getName());
        if (target == null || method.getParameterCount() != 0 || method.getReturnType() != boolean.class) {
            return false;
        }
        for (Class<?> parent = method.getDeclaringClass().getSuperclass(); parent != null; parent = parent.getSuperclass()) {
            for (Method candidate : parent.getDeclaredMethods()) {
                if (candidate.getName().equals(target) && candidate.getParameterCount() == 0 && candidate.getReturnType() == boolean.class
                    && Modifier.isAbstract(candidate.getModifiers())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean sequence(ClassNode type, MethodNode method) {
        if (!method.desc.startsWith("(III)L") || method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            return false;
        }
        var code = new java.util.ArrayList<org.objectweb.asm.tree.AbstractInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) code.add(instruction);
        }
        int[] opcodes = {Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.INVOKEINTERFACE, Opcodes.ISTORE,
            Opcodes.ICONST_0, Opcodes.ISTORE, Opcodes.ILOAD, Opcodes.ILOAD, Opcodes.IF_ICMPGE,
            Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.ILOAD, Opcodes.INVOKEINTERFACE, Opcodes.CHECKCAST,
            Opcodes.ILOAD, Opcodes.ILOAD, Opcodes.ILOAD, Opcodes.INVOKEINTERFACE, Opcodes.ASTORE,
            Opcodes.ALOAD, Opcodes.IFNULL, Opcodes.ALOAD, Opcodes.ARETURN, Opcodes.IINC, Opcodes.GOTO,
            Opcodes.ACONST_NULL, Opcodes.ARETURN};
        if (code.size() != opcodes.length) return false;
        for (int i = 0; i < opcodes.length; i++) {
            if (code.get(i).getOpcode() != opcodes[i]) return false;
        }
        int[] indices = {0, 3, 5, 6, 7, 9, 11, 14, 15, 16, 18, 19, 21};
        int[] variables = {0, 4, 5, 5, 4, 0, 5, 1, 2, 3, 6, 6, 6};
        for (int i = 0; i < indices.length; i++) {
            if (((VarInsnNode) code.get(indices[i])).var != variables[i]) return false;
        }
        FieldInsnNode first = (FieldInsnNode) code.get(1);
        FieldInsnNode second = (FieldInsnNode) code.get(10);
        MethodInsnNode size = (MethodInsnNode) code.get(2);
        MethodInsnNode get = (MethodInsnNode) code.get(12);
        MethodInsnNode apply = (MethodInsnNode) code.get(17);
        IincInsnNode increment = (IincInsnNode) code.get(23);
        if (!first.owner.equals(type.name) || !first.desc.equals("Ljava/util/List;")
            || !second.owner.equals(first.owner) || !second.name.equals(first.name) || !second.desc.equals(first.desc)
            || !size.owner.equals("java/util/List") || !size.name.equals("size") || !size.desc.equals("()I")
            || !get.owner.equals("java/util/List") || !get.name.equals("get") || !get.desc.equals("(I)Ljava/lang/Object;")
            || !type.interfaces.contains(apply.owner) || !apply.name.equals(method.name) || !apply.desc.equals(method.desc)
            || !((TypeInsnNode) code.get(13)).desc.equals(apply.owner) || increment.var != 5 || increment.incr != 1) {
            return false;
        }
        if (type.recordComponents == null || type.recordComponents.size() != 1
            || !type.recordComponents.get(0).name.equals(first.name) || !type.recordComponents.get(0).descriptor.equals(first.desc)) {
            return false;
        }
        return destination((JumpInsnNode) code.get(8)) == code.get(25)
            && destination((JumpInsnNode) code.get(20)) == code.get(23)
            && destination((JumpInsnNode) code.get(24)) == code.get(6);
    }

    private static org.objectweb.asm.tree.AbstractInsnNode destination(JumpInsnNode jump) {
        var instruction = (org.objectweb.asm.tree.AbstractInsnNode) jump.label;
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static boolean foreign(java.util.List<AnnotationNode> annotations) {
        if (annotations != null) {
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;") && annotation.values != null) {
                    for (int i = 0; i < annotation.values.size(); i += 2) {
                        if (annotation.values.get(i).equals("mixin") && annotation.values.get(i + 1) instanceof String owner
                            && !owner.startsWith("net.fly.adrenaline.mixin.")) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
