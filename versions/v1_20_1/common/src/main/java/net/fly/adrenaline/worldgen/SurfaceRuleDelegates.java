package net.fly.adrenaline.worldgen;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

final class SurfaceRuleDelegates {

    private record Shape(Field source, boolean grid) { }
    private static final ClassValue<Shape> SHAPES = new ClassValue<>() {
        @Override
        protected Shape computeValue(Class<?> type) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                if (stream == null) return new Shape(null, false);
                ClassNode node = new ClassNode();
                new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                Field source = null;
                boolean grid = false;
                for (MethodNode method : node.methods) {
                    var code = code(method);
                    if (method.name.equals("apply") && method.desc.equals("(" + Type.getDescriptor(SurfaceRulesContextFactory.contextType()) + ")" + ruleType(node))) {
                        if (code.size() == 6 && variable(code.get(0), Opcodes.ALOAD, 0) && code.get(1) instanceof FieldInsnNode field
                            && field.getOpcode() == Opcodes.GETFIELD && field.owner.equals(node.name) && field.desc.equals(Type.getDescriptor(SurfaceRules.RuleSource.class))
                            && variable(code.get(2), Opcodes.ALOAD, 1) && code.get(3) instanceof MethodInsnNode call
                            && call.getOpcode() == Opcodes.INVOKEINTERFACE && call.owner.equals(Type.getInternalName(SurfaceRules.RuleSource.class))
                            && call.name.equals("apply") && call.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;")
                            && code.get(4) instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST
                            && ("L" + cast.desc + ";").equals(ruleType(node)) && code.get(5).getOpcode() == Opcodes.ARETURN) {
                            source = type.getDeclaredField(field.name);
                            source.setAccessible(true);
                        }
                    }
                    for (var instruction : method.instructions) {
                        if (instruction instanceof InvokeDynamicInsnNode dynamic && dynamic.desc.startsWith("([[")) {
                            for (Object argument : dynamic.bsmArgs) {
                                if (argument instanceof Handle handle && handle.getOwner().equals(node.name)) {
                                    for (MethodNode target : node.methods) {
                                        if (target.name.equals(handle.getName()) && target.desc.equals(handle.getDesc()) && selector(target, dynamic.name)) grid = true;
                                    }
                                }
                            }
                        }
                    }
                }
                return new Shape(source, grid && stableFactory(node));
            } catch (IOException | ReflectiveOperationException | RuntimeException exception) {
                return new Shape(null, false);
            }
        }
    };

    private SurfaceRuleDelegates() { }

    static Field source(Class<?> type) {
        return SHAPES.get(type).source;
    }

    static Field grid(Class<?> source, Object runtime, Class<?> rule) {
        if (!SHAPES.get(source).grid || !runtime.getClass().getName().startsWith(source.getName() + "$$Lambda$")) return null;
        Field result = null;
        for (Field field : runtime.getClass().getDeclaredFields()) {
            Class<?> type = field.getType();
            if (!type.isArray() || !type.getComponentType().isArray() || type.getComponentType().getComponentType() != rule || result != null) return null;
            field.setAccessible(true);
            result = field;
        }
        return result;
    }

    private static String ruleType(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (method.name.equals("apply") && method.desc.startsWith("(" + Type.getDescriptor(SurfaceRulesContextFactory.contextType()) + ")")) {
                return Type.getReturnType(method.desc).getDescriptor();
            }
        }
        return "";
    }

    private static boolean stableFactory(ClassNode owner) {
        for (MethodNode method : owner.methods) {
            if (!method.name.equals("apply") || !method.desc.equals("(" + Type.getDescriptor(SurfaceRulesContextFactory.contextType()) + ")" + ruleType(owner))) continue;
            var code = code(method);
            if (!method.tryCatchBlocks.isEmpty()) return false;
            boolean positional = false;
            boolean provider = false;
            boolean allocation = false;
            int calls = 0;
            for (int i = 0; i < code.size(); i++) {
                var instruction = code.get(i);
                if (instruction instanceof FieldInsnNode field) {
                    if (field.getOpcode() != Opcodes.GETFIELD) return false;
                    if (!(field.owner.equals(owner.name) && field.desc.equals(Type.getDescriptor(net.minecraft.util.random.SimpleWeightedRandomList.class)))
                        && !(field.owner.equals(Type.getInternalName(SurfaceRulesContextFactory.contextType())) && field.desc.equals(Type.getDescriptor(net.minecraft.world.level.levelgen.SurfaceSystem.class)))
                        && !(field.owner.equals(Type.getInternalName(net.minecraft.world.level.levelgen.SurfaceSystem.class)) && field.desc.equals(Type.getDescriptor(net.minecraft.world.level.levelgen.PositionalRandomFactory.class)))) return false;
                } else if (instruction instanceof MethodInsnNode call) {
                    calls++;
                    if (call.owner.equals(Type.getInternalName(net.minecraft.world.level.levelgen.PositionalRandomFactory.class))
                        && call.desc.equals("(III)" + Type.getDescriptor(net.minecraft.util.RandomSource.class))) {
                        if (i < 4 || !variable(code.get(i - 4), Opcodes.ALOAD, 2) || !variable(code.get(i - 3), Opcodes.ILOAD, 4)
                            || code.get(i - 2).getOpcode() != Opcodes.ICONST_0 || !variable(code.get(i - 1), Opcodes.ILOAD, 5)) return false;
                        positional = true;
                    } else if (call.owner.equals(Type.getInternalName(net.minecraft.util.random.SimpleWeightedRandomList.class))
                        && call.desc.equals("(" + Type.getDescriptor(net.minecraft.util.RandomSource.class) + ")Ljava/util/Optional;")) provider = true;
                    else if (!(call.owner.equals("java/util/Optional") && call.name.equals("get") && call.desc.equals("()Ljava/lang/Object;"))
                        && !(call.owner.equals(Type.getInternalName(SurfaceRules.RuleSource.class)) && call.name.equals("apply") && call.desc.equals("(Ljava/lang/Object;)Ljava/lang/Object;"))) return false;
                } else if (instruction instanceof VarInsnNode variable) {
                    if (variable.getOpcode() == Opcodes.ISTORE && (i == 0 || code.get(i - 1).getOpcode() != Opcodes.ICONST_0 || variable.var != 4 && variable.var != 5)) return false;
                    if (variable.getOpcode() == Opcodes.ASTORE && variable.var != 2 && variable.var != 3 && variable.var != 6) return false;
                    if (variable.getOpcode() != Opcodes.ALOAD && variable.getOpcode() != Opcodes.ASTORE && variable.getOpcode() != Opcodes.ILOAD && variable.getOpcode() != Opcodes.ISTORE) return false;
                } else if (instruction instanceof IincInsnNode increment) {
                    if (increment.incr != 1 || increment.var != 4 && increment.var != 5) return false;
                } else if (instruction instanceof IntInsnNode integer) {
                    if (integer.getOpcode() != Opcodes.BIPUSH || integer.operand != 16) return false;
                } else if (instruction instanceof MultiANewArrayInsnNode array) {
                    if (array.dims != 2 || i < 2 || !(code.get(i - 1) instanceof IntInsnNode one) || one.operand != 16
                        || !(code.get(i - 2) instanceof IntInsnNode two) || two.operand != 16) return false;
                    allocation = true;
                } else if (instruction instanceof JumpInsnNode jump) {
                    if (jump.getOpcode() != Opcodes.IF_ICMPGE && jump.getOpcode() != Opcodes.GOTO) return false;
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    if (!dynamic.bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory")
                        || !dynamic.desc.equals("([[" + ruleType(owner) + ")" + ruleType(owner))) return false;
                    boolean selected = false;
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle && handle.getOwner().equals(owner.name)) {
                            for (MethodNode target : owner.methods) {
                                if (target.name.equals(handle.getName()) && target.desc.equals(handle.getDesc()) && selector(target, dynamic.name)) selected = true;
                            }
                        }
                    }
                    if (!selected) return false;
                } else if (!(instruction instanceof TypeInsnNode cast && cast.getOpcode() == Opcodes.CHECKCAST)
                    && instruction.getOpcode() != Opcodes.ICONST_0
                    && instruction.getOpcode() != Opcodes.AALOAD && instruction.getOpcode() != Opcodes.AASTORE
                    && instruction.getOpcode() != Opcodes.ARRAYLENGTH && instruction.getOpcode() != Opcodes.ARETURN) return false;
            }
            return positional && provider && allocation && calls == 4;
        }
        return false;
    }

    private static boolean selector(MethodNode method, String name) {
        if (!method.desc.startsWith("([[L") || !method.desc.endsWith("III)" + Type.getDescriptor(BlockState.class)) || !method.tryCatchBlocks.isEmpty()) return false;
        var code = code(method);
        int[] opcodes = {Opcodes.ALOAD, Opcodes.ILOAD, Opcodes.BIPUSH, Opcodes.IAND, Opcodes.AALOAD,
            Opcodes.ILOAD, Opcodes.BIPUSH, Opcodes.IAND, Opcodes.AALOAD, Opcodes.ILOAD, Opcodes.ILOAD,
            Opcodes.ILOAD, Opcodes.INVOKEINTERFACE, Opcodes.ARETURN};
        if (code.size() != opcodes.length) return false;
        for (int i = 0; i < opcodes.length; i++) if (code.get(i).getOpcode() != opcodes[i]) return false;
        int[] indices = {0, 1, 5, 9, 10, 11};
        int[] variables = {0, 1, 3, 1, 2, 3};
        for (int i = 0; i < indices.length; i++) if (((VarInsnNode) code.get(indices[i])).var != variables[i]) return false;
        MethodInsnNode call = (MethodInsnNode) code.get(12);
        return ((IntInsnNode) code.get(2)).operand == 15 && ((IntInsnNode) code.get(6)).operand == 15
            && call.name.equals(name) && method.desc.equals("([[L" + call.owner + ";III)" + Type.getDescriptor(BlockState.class))
            && call.desc.equals("(III)" + Type.getDescriptor(BlockState.class));
    }

    private static boolean variable(AbstractInsnNode instruction, int opcode, int index) {
        return instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == index;
    }

    private static ArrayList<AbstractInsnNode> code(MethodNode method) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }
}
