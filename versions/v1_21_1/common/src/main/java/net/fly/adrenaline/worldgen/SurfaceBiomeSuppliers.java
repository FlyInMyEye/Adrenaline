package net.fly.adrenaline.worldgen;

import java.io.IOException;
import java.util.ArrayList;
import com.google.common.base.Suppliers;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.minecraft.core.Holder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class SurfaceBiomeSuppliers {

    private static final ClassValue<Boolean> MEMOIZED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            if (SurfacePipelineCompatibility.hasForeignHooks(type)) return false;
            if (type.getEnclosingClass() == Suppliers.class
                && (type.getSimpleName().equals("MemoizingSupplier") || type.getSimpleName().equals("NonSerializableMemoizingSupplier"))) {
                return true;
            }
            return positional(type);
        }
    };

    private SurfaceBiomeSuppliers() {
    }

    public static boolean memoized(Class<?> type) {
        return MEMOIZED.get(type);
    }

    private static boolean positional(Class<?> type) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (var stream = type.getResourceAsStream(resource)) {
            if (stream == null) return false;
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : node.methods) {
                if (method.name.equals("get") && method.desc.equals("()" + Type.getDescriptor(Holder.class)) && memoized(node, method)) {
                    return true;
                }
            }
        } catch (IOException | RuntimeException exception) {
            return false;
        }
        return false;
    }

    private static boolean memoized(ClassNode type, MethodNode method) {
        if ((method.access & Opcodes.ACC_STATIC) != 0 || !method.tryCatchBlocks.isEmpty()) return false;
        var code = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction.getOpcode() >= 0) code.add(instruction);
        }
        int[] opcodes = {Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.ASTORE, Opcodes.ALOAD, Opcodes.IFNONNULL,
            Opcodes.ALOAD, Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.ALOAD, Opcodes.GETFIELD,
            Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.ALOAD, Opcodes.GETFIELD, Opcodes.ALOAD, Opcodes.GETFIELD,
            Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE, Opcodes.CHECKCAST, Opcodes.DUP, Opcodes.ASTORE,
            Opcodes.PUTFIELD, Opcodes.ALOAD, Opcodes.ARETURN};
        if (code.size() != opcodes.length) return false;
        for (int i = 0; i < opcodes.length; i++) {
            if (code.get(i).getOpcode() != opcodes[i]) return false;
            if (code.get(i) instanceof VarInsnNode variable && variable.var != (i == 2 || i == 3 || i == 20 || i == 22 ? 1 : 0)) return false;
        }
        FieldInsnNode read = (FieldInsnNode) code.get(1);
        FieldInsnNode write = (FieldInsnNode) code.get(21);
        if (!read.owner.equals(type.name) || !read.name.equals(write.name) || !read.desc.equals(write.desc)
            || !write.owner.equals(type.name) || !read.desc.equals(Type.getDescriptor(Holder.class))) return false;
        for (int index : new int[]{7, 9, 11, 13, 15}) {
            if (!((FieldInsnNode) code.get(index)).owner.equals(type.name)) return false;
        }
        MethodInsnNode position = (MethodInsnNode) code.get(16);
        MethodInsnNode apply = (MethodInsnNode) code.get(17);
        if (!position.desc.startsWith("(III)L") || !apply.owner.equals("java/util/function/Function")
            || !apply.name.equals("apply") || !apply.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")
            || !((TypeInsnNode) code.get(18)).desc.equals(Type.getInternalName(Holder.class))) return false;
        AbstractInsnNode destination = ((JumpInsnNode) code.get(4)).label;
        while (destination != null && destination.getOpcode() < 0) destination = destination.getNext();
        return destination == code.get(22);
    }
}
