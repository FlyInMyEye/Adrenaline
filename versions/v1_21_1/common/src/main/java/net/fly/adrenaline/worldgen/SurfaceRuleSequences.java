package net.fly.adrenaline.worldgen;

import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

final class SurfaceRuleSequences {

    record Layout(Field sources, Field original, Class<?> runtime, Field rules) { }

    private static final ClassValue<Layout> LAYOUTS = new ClassValue<>() {
        @Override
        protected Layout computeValue(Class<?> type) {
            var components = type.getRecordComponents();
            if (components == null || components.length < 1 || components.length > 2) return null;
            try {
                Field sources = null;
                Field original = null;
                for (var component : components) {
                    if (component.getType() == List.class && sources == null) sources = type.getDeclaredField(component.getName());
                    else if (component.getType() == SurfaceRules.RuleSource.class && original == null) original = type.getDeclaredField(component.getName());
                    else return null;
                }
                if (sources == null) return null;
                ClassNode source = read(type);
                String context = Type.getDescriptor(SurfaceRulesContextFactory.contextType());
                for (MethodNode method : source.methods) {
                    if (!method.name.equals("apply") || !method.desc.startsWith("(" + context + ")")) continue;
                    String rule = Type.getReturnType(method.desc).getInternalName();
                    String runtime = null;
                    for (var instruction : method.instructions) {
                        if (instruction instanceof TypeInsnNode allocation && allocation.getOpcode() == NEW) {
                            if (runtime != null) return null;
                            runtime = allocation.desc;
                        }
                    }
                    if (runtime == null || !matches(method, factory(source.name, method.desc, rule, runtime, sources.getName(), original == null ? null : original.getName()))) return null;
                    Class<?> runtimeType = Class.forName(runtime.replace('/', '.'), false, type.getClassLoader());
                    var runtimeComponents = runtimeType.getRecordComponents();
                    if (runtimeComponents == null || runtimeComponents.length != 1 || runtimeComponents[0].getType() != List.class) return null;
                    ClassNode applied = read(runtimeType);
                    if (!applied.interfaces.contains(rule)) return null;
                    Field rules = runtimeType.getDeclaredField(runtimeComponents[0].getName());
                    String descriptor = "(III)" + Type.getDescriptor(BlockState.class);
                    boolean evaluator = false;
                    boolean constructor = false;
                    for (MethodNode candidate : applied.methods) {
                        if (candidate.desc.equals(descriptor) && matches(candidate, evaluator(runtime, rules.getName(), rule, candidate.name, descriptor))) evaluator = true;
                        if (candidate.name.equals("<init>") && candidate.desc.equals("(Ljava/util/List;)V")
                            && matches(candidate, constructor(runtime, rules.getName()))) constructor = true;
                    }
                    if (!evaluator || !constructor) return null;
                    sources.setAccessible(true);
                    if (original != null) original.setAccessible(true);
                    rules.setAccessible(true);
                    return new Layout(sources, original, runtimeType, rules);
                }
            } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
                return null;
            }
            return null;
        }
    };

    private SurfaceRuleSequences() { }

    static Layout get(Class<?> type) {
        return LAYOUTS.get(type);
    }

    private static ClassNode read(Class<?> type) throws IOException {
        try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            if (stream == null) throw new IOException("Missing surface rule class");
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static MethodNode factory(String owner, String descriptor, String rule, String runtime, String sources, String original) {
        MethodNode method = new MethodNode();
        Label multiple = new Label(), loop = new Label(), end = new Label();
        String source = Type.getInternalName(SurfaceRules.RuleSource.class);
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, owner, sources, "Ljava/util/List;");
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "size", "()I", true);
        method.visitInsn(ICONST_1);
        method.visitJumpInsn(IF_ICMPNE, multiple);
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, owner, sources, "Ljava/util/List;");
        method.visitInsn(ICONST_0);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
        method.visitTypeInsn(CHECKCAST, source);
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKEINTERFACE, source, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        method.visitTypeInsn(CHECKCAST, rule);
        method.visitInsn(ARETURN);
        method.visitLabel(multiple);
        method.visitMethodInsn(INVOKESTATIC, "com/google/common/collect/ImmutableList", "builder", "()Lcom/google/common/collect/ImmutableList$Builder;", false);
        method.visitVarInsn(ASTORE, 2);
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, owner, sources, "Ljava/util/List;");
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
        method.visitVarInsn(ASTORE, 3);
        method.visitLabel(loop);
        method.visitVarInsn(ALOAD, 3);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
        method.visitJumpInsn(IFEQ, end);
        method.visitVarInsn(ALOAD, 3);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
        method.visitTypeInsn(CHECKCAST, source);
        method.visitVarInsn(ASTORE, 4);
        method.visitVarInsn(ALOAD, 2);
        method.visitVarInsn(ALOAD, 4);
        method.visitVarInsn(ALOAD, 1);
        method.visitMethodInsn(INVOKEINTERFACE, source, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        method.visitTypeInsn(CHECKCAST, rule);
        method.visitMethodInsn(INVOKEVIRTUAL, "com/google/common/collect/ImmutableList$Builder", "add", "(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;", false);
        method.visitInsn(POP);
        method.visitJumpInsn(GOTO, loop);
        method.visitLabel(end);
        if (original != null) {
            method.visitVarInsn(ALOAD, 2);
            method.visitVarInsn(ALOAD, 0);
            method.visitFieldInsn(GETFIELD, owner, original, Type.getDescriptor(SurfaceRules.RuleSource.class));
            method.visitVarInsn(ALOAD, 1);
            method.visitMethodInsn(INVOKEINTERFACE, source, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            method.visitTypeInsn(CHECKCAST, rule);
            method.visitMethodInsn(INVOKEVIRTUAL, "com/google/common/collect/ImmutableList$Builder", "add", "(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;", false);
            method.visitInsn(POP);
        }
        method.visitTypeInsn(NEW, runtime);
        method.visitInsn(DUP);
        method.visitVarInsn(ALOAD, 2);
        method.visitMethodInsn(INVOKEVIRTUAL, "com/google/common/collect/ImmutableList$Builder", "build", "()Lcom/google/common/collect/ImmutableList;", false);
        method.visitMethodInsn(INVOKESPECIAL, runtime, "<init>", "(Ljava/util/List;)V", false);
        method.visitInsn(ARETURN);
        return method;
    }

    private static MethodNode evaluator(String owner, String rules, String rule, String name, String descriptor) {
        MethodNode method = new MethodNode();
        Label loop = new Label(), next = new Label(), end = new Label();
        method.visitVarInsn(ALOAD, 0);
        method.visitFieldInsn(GETFIELD, owner, rules, "Ljava/util/List;");
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "iterator", "()Ljava/util/Iterator;", true);
        method.visitVarInsn(ASTORE, 4);
        method.visitLabel(loop);
        method.visitVarInsn(ALOAD, 4);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "hasNext", "()Z", true);
        method.visitJumpInsn(IFEQ, end);
        method.visitVarInsn(ALOAD, 4);
        method.visitMethodInsn(INVOKEINTERFACE, "java/util/Iterator", "next", "()Ljava/lang/Object;", true);
        method.visitTypeInsn(CHECKCAST, rule);
        method.visitVarInsn(ASTORE, 5);
        method.visitVarInsn(ALOAD, 5);
        method.visitVarInsn(ILOAD, 1);
        method.visitVarInsn(ILOAD, 2);
        method.visitVarInsn(ILOAD, 3);
        method.visitMethodInsn(INVOKEINTERFACE, rule, name, descriptor, true);
        method.visitVarInsn(ASTORE, 6);
        method.visitVarInsn(ALOAD, 6);
        method.visitJumpInsn(IFNULL, next);
        method.visitVarInsn(ALOAD, 6);
        method.visitInsn(ARETURN);
        method.visitLabel(next);
        method.visitJumpInsn(GOTO, loop);
        method.visitLabel(end);
        method.visitInsn(ACONST_NULL);
        method.visitInsn(ARETURN);
        return method;
    }

    private static MethodNode constructor(String owner, String rules) {
        MethodNode method = new MethodNode();
        method.visitVarInsn(ALOAD, 0);
        method.visitMethodInsn(INVOKESPECIAL, "java/lang/Record", "<init>", "()V", false);
        method.visitVarInsn(ALOAD, 0);
        method.visitVarInsn(ALOAD, 1);
        method.visitFieldInsn(PUTFIELD, owner, rules, "Ljava/util/List;");
        method.visitInsn(RETURN);
        return method;
    }

    private static boolean matches(MethodNode actual, MethodNode expected) {
        if ((actual.access & (ACC_STATIC | ACC_SYNCHRONIZED | ACC_NATIVE | ACC_ABSTRACT)) != 0 || !actual.tryCatchBlocks.isEmpty()) return false;
        List<AbstractInsnNode> left = code(actual), right = code(expected);
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            AbstractInsnNode a = left.get(i), b = right.get(i);
            if (a.getOpcode() != b.getOpcode() || a.getType() != b.getType()) return false;
            if (a instanceof VarInsnNode x && x.var != ((VarInsnNode) b).var) return false;
            if (a instanceof FieldInsnNode x && b instanceof FieldInsnNode y && (!x.owner.equals(y.owner) || !x.name.equals(y.name) || !x.desc.equals(y.desc))) return false;
            if (a instanceof MethodInsnNode x && b instanceof MethodInsnNode y && (!x.owner.equals(y.owner) || !x.name.equals(y.name) || !x.desc.equals(y.desc) || x.itf != y.itf)) return false;
            if (a instanceof TypeInsnNode x && !x.desc.equals(((TypeInsnNode) b).desc)) return false;
            if (a instanceof IntInsnNode x && x.operand != ((IntInsnNode) b).operand) return false;
            if (a instanceof JumpInsnNode x && b instanceof JumpInsnNode y && left.indexOf(destination(x)) != right.indexOf(destination(y))) return false;
        }
        return true;
    }

    private static AbstractInsnNode destination(JumpInsnNode jump) {
        AbstractInsnNode instruction = jump.label;
        while (instruction != null && instruction.getOpcode() < 0) instruction = instruction.getNext();
        return instruction;
    }

    private static List<AbstractInsnNode> code(MethodNode method) {
        List<AbstractInsnNode> result = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
}
