package net.fly.adrenaline.compat;

import static org.objectweb.asm.Opcodes.*;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class OreCandidateBytecode {

    private static final String CANDIDATES = "net/fly/adrenaline/worldgen/OreCandidateBatch";
    private static final String ORE_MIXIN = "net.fly.adrenaline.mixin.MixinOreFeature";

    private OreCandidateBytecode() {
    }

    public static void apply(ClassNode type, String mixin) {
        if (!ORE_MIXIN.equals(mixin)) {
            return;
        }
        for (MethodNode method : type.methods) {
            Type[] args = Type.getArgumentTypes(method.desc);
            if ((method.access & ACC_STATIC) == 0 && args.length == 14 && args[0].getSort() == Type.OBJECT
                && args[1].getSort() == Type.OBJECT && args[2].getSort() == Type.OBJECT
                && method.desc.endsWith("DDDDDDIIIII)Z")) {
                patch(method, args[0].getDescriptor());
                return;
            }
        }
    }

    private static void patch(MethodNode method, String levelType) {
        MethodInsnNode visitedGet = null;
        for (AbstractInsnNode node : method.instructions) {
            if (node instanceof MethodInsnNode call && call.owner.equals("java/util/BitSet")
                && call.name.equals("get") && call.desc.equals("(I)Z")) {
                if (visitedGet != null) {
                    return;
                }
                visitedGet = call;
            }
        }
        if (visitedGet == null || !variable(previous(visitedGet), ILOAD, 51)
            || !variable(previous(previous(visitedGet)), ALOAD, 22)) {
            return;
        }
        JumpInsnNode duplicate = jump(next(visitedGet), IFEQ);
        JumpInsnNode skip = duplicate == null ? null : jump(next(duplicate), GOTO);
        if (skip == null || !(next(skip.label) instanceof IincInsnNode zIncrement) || zIncrement.var != 48
            || zIncrement.incr != 1) {
            return;
        }
        JumpInsnNode zBack = jump(next(zIncrement), GOTO);
        if (zBack == null) {
            return;
        }
        AbstractInsnNode zStore = previous(zBack.label);
        if (!variable(zStore, ISTORE, 48) || !variable(previous(zStore), ILOAD, 38)) {
            return;
        }
        AbstractInsnNode xStart = null;
        JumpInsnNode xExit = null;
        for (AbstractInsnNode node = previous(zStore); node != null; node = previous(node)) {
            if (variable(node, ISTORE, 42) && variable(previous(node), ILOAD, 36)) {
                AbstractInsnNode first = next(node);
                AbstractInsnNode second = next(first);
                xStart = previous(node);
                if (variable(first, ILOAD, 42) && variable(second, ILOAD, 39)) {
                    xExit = jump(next(second), IF_ICMPGT);
                }
                break;
            }
        }
        if (xExit == null || !(next(xExit.label) instanceof IincInsnNode sphereIncrement)
            || sphereIncrement.var != 27 || sphereIncrement.incr != 1) {
            return;
        }
        JumpInsnNode sphereBack = jump(next(sphereIncrement), GOTO);
        if (sphereBack == null || !variable(previous(sphereBack.label), ISTORE, 27)) {
            return;
        }
        AbstractInsnNode sphereStart = previous(previous(sphereBack.label));
        if (sphereStart == null || sphereStart.getOpcode() != ICONST_0 || !variable(previous(sphereStart), ASTORE, 26)) {
            return;
        }
        AbstractInsnNode indexStart = previous(previous(visitedGet));
        int[] indexOpcodes = { ISTORE, IADD, IMUL, ILOAD, IMUL, ILOAD, ISUB, ILOAD, ILOAD,
            IADD, IMUL, ILOAD, ISUB, ILOAD, ILOAD, ISUB, ILOAD, ILOAD };
        int[] indexSlots = { 51, 20, 19, 18, 48, 19, 17, 45, 16, 42 };
        int slotIndex = 0;
        for (int opcode : indexOpcodes) {
            indexStart = previous(indexStart);
            if (indexStart == null || indexStart.getOpcode() != opcode) {
                return;
            }
            if (indexStart instanceof VarInsnNode variable && variable.var != indexSlots[slotIndex++]) {
                return;
            }
        }
        MethodInsnNode heightCheck = null;
        for (AbstractInsnNode node = xStart; node != indexStart; node = node.getNext()) {
            if (node == null) {
                return;
            }
            switch (node.getOpcode()) {
                case -1, ALOAD, ILOAD, ISTORE, IF_ICMPGT, I2D, LDC, DADD, DLOAD, DSUB, DDIV, DSTORE, DMUL,
                    DCONST_1, DCMPG, IFGE, IFEQ, GOTO -> { }
                case INVOKEINTERFACE -> {
                    MethodInsnNode call = (MethodInsnNode) node;
                    if (heightCheck != null || !call.owner.equals(levelType.substring(1, levelType.length() - 1))
                        || !call.desc.equals("(I)Z")) {
                        return;
                    }
                    heightCheck = call;
                }
                default -> {
                    return;
                }
            }
        }
        if (heightCheck == null || !variable(previous(heightCheck), ILOAD, 45)
            || !variable(previous(previous(heightCheck)), ALOAD, 1)) {
            return;
        }

        int batch = method.maxLocals++;
        int packed = method.maxLocals++;
        int cursor = method.maxLocals++;
        InsnList setup = new InsnList();
        setup.add(new VarInsnNode(ALOAD, 1));
        setup.add(new VarInsnNode(ALOAD, 25));
        for (int slot = 16; slot <= 20; slot++) {
            setup.add(new VarInsnNode(ILOAD, slot));
        }
        setup.add(new MethodInsnNode(INVOKESTATIC, CANDIDATES, "prepare", "(" + levelType + "[DIIIII)[I", false));
        setup.add(new VarInsnNode(ASTORE, batch));
        setup.add(new InsnNode(ICONST_0));
        setup.add(new VarInsnNode(ISTORE, cursor));
        method.instructions.insertBefore(sphereStart, setup);

        LabelNode dispatch = new LabelNode();
        LabelNode vanilla = new LabelNode();
        LabelNode indexEntry = new LabelNode();
        method.instructions.insertBefore(indexStart, indexEntry);
        InsnList replay = new InsnList();
        replay.add(new VarInsnNode(ALOAD, batch));
        replay.add(new JumpInsnNode(IFNULL, vanilla));
        replay.add(dispatch);
        replay.add(new VarInsnNode(ILOAD, cursor));
        replay.add(new VarInsnNode(ALOAD, batch));
        replay.add(new InsnNode(ARRAYLENGTH));
        replay.add(new JumpInsnNode(IF_ICMPGE, xExit.label));
        replay.add(new VarInsnNode(ALOAD, batch));
        replay.add(new VarInsnNode(ILOAD, cursor));
        replay.add(new InsnNode(IALOAD));
        replay.add(new VarInsnNode(ISTORE, packed));
        replay.add(new VarInsnNode(ILOAD, packed));
        replay.add(new IntInsnNode(BIPUSH, 24));
        replay.add(new InsnNode(IUSHR));
        replay.add(new VarInsnNode(ILOAD, 27));
        replay.add(new JumpInsnNode(IF_ICMPNE, xExit.label));
        replay.add(new IincInsnNode(cursor, 1));
        coordinate(replay, packed, 0, 16, 42, 30, 43);
        coordinate(replay, packed, 8, 17, 45, 32, 46);
        coordinate(replay, packed, 16, 18, 48, 34, 49);
        replay.add(new JumpInsnNode(GOTO, indexEntry));
        replay.add(vanilla);
        method.instructions.insertBefore(xStart, replay);

        InsnList resume = new InsnList();
        resume.add(new VarInsnNode(ALOAD, batch));
        resume.add(new JumpInsnNode(IFNONNULL, dispatch));
        method.instructions.insertBefore(zIncrement, resume);
    }

    private static void coordinate(InsnList code, int packed, int shift, int origin, int position, int center, int distance) {
        code.add(new VarInsnNode(ILOAD, packed));
        if (shift > 0) {
            code.add(new IntInsnNode(BIPUSH, shift));
            code.add(new InsnNode(IUSHR));
        }
        code.add(new IntInsnNode(SIPUSH, 255));
        code.add(new InsnNode(IAND));
        code.add(new VarInsnNode(ILOAD, origin));
        code.add(new InsnNode(IADD));
        code.add(new InsnNode(DUP));
        code.add(new VarInsnNode(ISTORE, position));
        code.add(new InsnNode(I2D));
        code.add(new LdcInsnNode(0.5));
        code.add(new InsnNode(DADD));
        code.add(new VarInsnNode(DLOAD, center));
        code.add(new InsnNode(DSUB));
        code.add(new VarInsnNode(DLOAD, 28));
        code.add(new InsnNode(DDIV));
        code.add(new VarInsnNode(DSTORE, distance));
    }

    private static boolean variable(AbstractInsnNode node, int opcode, int slot) {
        return node instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == slot;
    }

    private static JumpInsnNode jump(AbstractInsnNode node, int opcode) {
        return node instanceof JumpInsnNode jump && jump.getOpcode() == opcode ? jump : null;
    }

    private static AbstractInsnNode previous(AbstractInsnNode node) {
        if (node != null) {
            node = node.getPrevious();
        }
        while (node != null && node.getOpcode() < 0) {
            node = node.getPrevious();
        }
        return node;
    }

    private static AbstractInsnNode next(AbstractInsnNode node) {
        if (node != null) {
            node = node.getNext();
        }
        while (node != null && node.getOpcode() < 0) {
            node = node.getNext();
        }
        return node;
    }
}
