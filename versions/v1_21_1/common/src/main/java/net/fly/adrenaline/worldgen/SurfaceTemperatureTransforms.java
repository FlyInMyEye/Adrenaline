package net.fly.adrenaline.worldgen;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import net.fly.adrenaline.compat.SurfacePipelineCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.Opcodes;

public final class SurfaceTemperatureTransforms {

    private record Model(String helper, String configuration, String movement) { }
    private static volatile Model model;
    private static volatile ClassNode positions;
    private static volatile ClassNode frozen;
    private static volatile String[] coordinates;

    private SurfaceTemperatureTransforms() { }

    public static void capture(ClassNode type) {
        if (target(type, "adrenaline$temperatureBelow") != null) positions = type;
        if (target(type, "adrenaline$modifyFrozenTemperature") != null) frozen = type;
        MethodNode x = target(type, "adrenaline$temperatureX");
        MethodNode y = target(type, "adrenaline$temperatureY");
        MethodNode z = target(type, "adrenaline$temperatureZ");
        if (x != null && y != null && z != null) coordinates = new String[]{x.name + x.desc, y.name + y.desc, z.name + z.desc};
        MethodNode cold = target(type, "adrenaline$coldEnoughToSnow");
        if (cold == null) return;
        MethodNode temperature = target(type, "adrenaline$getTemperature");
        MethodNode adjusted = target(type, "adrenaline$getHeightAdjustedTemperature");
        ClassNode original = read(type.name);
        if (temperature == null || adjusted == null || original == null) {
            model = null;
            return;
        }
        var adjustedCode = code(adjusted);
        String helper = null;
        String configuration = null;
        String movement = null;
        int prefix = 0;
        if (adjustedCode.size() >= 4 && variable(adjustedCode.get(0), Opcodes.ALOAD, 0)
            && variable(adjustedCode.get(1), Opcodes.ALOAD, 1) && adjustedCode.get(2) instanceof MethodInsnNode call
            && call.owner.equals(type.name) && call.desc.equals("(" + argument(adjusted.desc) + ")" + argument(adjusted.desc))
            && variable(adjustedCode.get(3), Opcodes.ASTORE, 1)) {
            MethodNode transform = find(type, call.name, call.desc);
            configuration = configuration(transform, argument(adjusted.desc));
            if (configuration == null) {
                model = null;
                return;
            }
            helper = call.name;
            var transformCode = code(transform);
            MethodInsnNode move = (MethodInsnNode) transformCode.get(transformCode.size() - 2);
            movement = move.name + move.desc;
            prefix = 4;
        }
        var tempCode = code(temperature);
        boolean forwards = tempCode.size() == 4 && variable(tempCode.get(0), Opcodes.ALOAD, 0)
            && variable(tempCode.get(1), Opcodes.ALOAD, 1) && tempCode.get(2) instanceof MethodInsnNode call
            && call.owner.equals(type.name) && call.name.equals(adjusted.name) && call.desc.equals(adjusted.desc)
            && tempCode.get(3).getOpcode() == Opcodes.FRETURN;
        if (prefix != 0 && !forwards) {
            model = null;
            return;
        }
        Set<String> seen = new HashSet<>();
        ArrayDeque<MethodNode> pending = new ArrayDeque<>();
        pending.add(cold);
        while (!pending.isEmpty()) {
            MethodNode method = pending.remove();
            if (!seen.add(method.name + method.desc)) continue;
            int start = method == adjusted ? prefix : 0;
            boolean forwarding = method == temperature && forwards;
            if (!forwarding) {
                MethodNode vanilla = find(original, method.name, method.desc);
                if (vanilla == null || !fingerprint(method, start).equals(fingerprint(vanilla, 0))) {
                    model = null;
                    return;
                }
            }
            var instructions = code(method);
            for (int i = start; i < instructions.size(); i++) {
                if (instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(type.name)) {
                    MethodNode child = find(type, call.name, call.desc);
                    if (child == null) {
                        model = null;
                        return;
                    }
                    pending.add(child);
                }
            }
        }
        model = new Model(helper, configuration, movement);
    }

    public static boolean supported() {
        Model current = model;
        if (current == null) return false;
        if (current.configuration == null) return true;
        try {
            Class<?> configuration = Class.forName(current.configuration.replace('/', '.'), false, SurfaceTemperatureTransforms.class.getClassLoader());
            return !SurfacePipelineCompatibility.hasForeignHooks(configuration)
                && positionReaders() && pureMovement(current.movement);
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    static boolean frozenSupported(Class<?> type) {
        ClassNode actual = frozen;
        if (actual == null || !actual.name.replace('/', '.').equals(type.getName())) return false;
        MethodNode method = target(actual, "adrenaline$modifyFrozenTemperature");
        ClassNode original = read(actual.name);
        MethodNode vanilla = original == null || method == null ? null : find(original, method.name, method.desc);
        if (vanilla == null) return false;
        var code = code(method);
        if (code.size() < 4 || !variable(code.get(0), Opcodes.ALOAD, 0) || !variable(code.get(1), Opcodes.ALOAD, 1)
            || !(code.get(2) instanceof MethodInsnNode call) || !call.owner.equals(actual.name) || !variable(code.get(3), Opcodes.ASTORE, 1)) return false;
        MethodNode helper = find(actual, call.name, call.desc);
        String configuration = configuration(helper, argument(method.desc));
        if (configuration == null || !fingerprint(method, 4).equals(fingerprint(vanilla, 0))) return false;
        try {
            if (SurfacePipelineCompatibility.hasForeignHooks(Class.forName(configuration.replace('/', '.'), false, SurfaceTemperatureTransforms.class.getClassLoader()))) return false;
        } catch (ClassNotFoundException exception) {
            return false;
        }
        var helperCode = code(helper);
        MethodInsnNode move = (MethodInsnNode) helperCode.get(helperCode.size() - 2);
        String movement = move.name + move.desc;
        ClassNode position = positions;
        String[] axes = coordinates;
        if (position == null || axes == null || !pureMovement(movement) || !positionReaders()) return false;
        MethodNode above = target(position, "adrenaline$temperatureAbove");
        MethodNode below = target(position, "adrenaline$temperatureBelow");
        if (!(above != null && movement.equals(above.name + above.desc) || below != null && movement.equals(below.name + below.desc))) return false;
        MethodNode translated = find(position, move.name, move.desc);
        var body = code(translated);
        if (body.size() == 12 && body.get(0).getOpcode() == Opcodes.NEW && body.get(1).getOpcode() == Opcodes.DUP
            && variable(body.get(2), Opcodes.ALOAD, 0) && body.get(3) instanceof MethodInsnNode rx && (rx.name + rx.desc).equals(axes[0])
            && variable(body.get(4), Opcodes.ALOAD, 0) && body.get(5) instanceof MethodInsnNode ry && (ry.name + ry.desc).equals(axes[1])
            && variable(body.get(6), Opcodes.ILOAD, 1) && (body.get(7).getOpcode() == Opcodes.IADD || body.get(7).getOpcode() == Opcodes.ISUB)
            && variable(body.get(8), Opcodes.ALOAD, 0) && body.get(9) instanceof MethodInsnNode rz && (rz.name + rz.desc).equals(axes[2])
            && body.get(10) instanceof MethodInsnNode constructor && constructor.name.equals("<init>") && constructor.desc.equals("(III)V")
            && body.get(11).getOpcode() == Opcodes.ARETURN) return true;
        ClassNode canonical = read(position.name);
        ArrayDeque<MethodNode> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(translated);
        while (!pending.isEmpty()) {
            MethodNode current = pending.remove();
            if (!seen.add(current.name + current.desc)) continue;
            MethodNode expected = canonical == null ? null : find(canonical, current.name, current.desc);
            if (expected == null || !fingerprint(current, 0).equals(fingerprint(expected, 0))) return false;
            for (var instruction : current.instructions) {
                if (instruction instanceof MethodInsnNode nested && nested.owner.equals(position.name) && !nested.name.equals("<init>")
                    && !Arrays.asList(axes).contains(nested.name + nested.desc)) {
                    MethodNode next = find(position, nested.name, nested.desc);
                    if (next == null) return false;
                    pending.add(next);
                }
            }
        }
        return true;
    }

    private static boolean positionReaders() {
        ClassNode position = read(org.objectweb.asm.Type.getInternalName(net.minecraft.core.Vec3i.class));
        if (position == null) return false;
        int readers = 0;
        for (MethodNode method : position.methods) {
            var code = code(method);
            if (method.desc.equals("()I") && code.size() == 3 && variable(code.get(0), Opcodes.ALOAD, 0)
                && code.get(1) instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETFIELD && field.desc.equals("I")
                && code.get(2).getOpcode() == Opcodes.IRETURN) {
                readers++;
                for (Class<?> type : List.of(BlockPos.class, BlockPos.MutableBlockPos.class)) {
                    try {
                        var reader = type.getMethod(method.name);
                        if (SurfaceRuleHooks.hooked(reader.getDeclaringClass(), method.name + method.desc)) return false;
                    } catch (NoSuchMethodException exception) {
                        return false;
                    }
                }
            }
        }
        return readers == 3;
    }

    private static boolean pureMovement(String entry) {
        ClassNode actual = positions;
        if (actual == null || entry == null) return false;
        if (SurfaceRuleHooks.hooked(net.minecraft.core.Vec3i.class, "<init>(III)V")) return false;
        ClassNode original = read(actual.name);
        ClassNode vector = read(org.objectweb.asm.Type.getInternalName(net.minecraft.core.Vec3i.class));
        if (original == null || vector == null) return false;
        MethodNode constructor = find(actual, "<init>", "(III)V");
        MethodNode originalConstructor = find(original, "<init>", "(III)V");
        if (constructor == null || originalConstructor == null || !fingerprint(constructor, 0).equals(fingerprint(originalConstructor, 0))) return false;
        Set<String> readers = new HashSet<>();
        for (MethodNode method : vector.methods) {
            var code = code(method);
            if (method.desc.equals("()I") && code.size() == 3 && code.get(1).getOpcode() == Opcodes.GETFIELD) readers.add(method.name + method.desc);
        }
        ArrayDeque<String> pending = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        pending.add(entry);
        while (!pending.isEmpty()) {
            String key = pending.remove();
            if (!seen.add(key)) continue;
            MethodNode method = null;
            for (MethodNode candidate : actual.methods) if ((candidate.name + candidate.desc).equals(key)) method = candidate;
            if (method == null || !method.tryCatchBlocks.isEmpty()) return false;
            MethodNode vanilla = find(original, method.name, method.desc);
            if (vanilla != null && fingerprint(method, 0).equals(fingerprint(vanilla, 0))) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(actual.name) && !readers.contains(call.name + call.desc) && !call.name.equals("<init>")) pending.add(call.name + call.desc);
                }
                continue;
            }
            for (var instruction : code(method)) {
                int opcode = instruction.getOpcode();
                if (instruction instanceof MethodInsnNode call) {
                    if (!(call.owner.equals(actual.name) && (readers.contains(call.name + call.desc) || call.name.equals("<init>") && call.desc.equals("(III)V")))) return false;
                } else if (instruction instanceof TypeInsnNode type) {
                    if (opcode != Opcodes.NEW || !type.desc.equals(actual.name)) return false;
                } else if (instruction instanceof VarInsnNode variable) {
                    if (!(opcode == Opcodes.ALOAD && variable.var == 0 || opcode == Opcodes.ILOAD && variable.var >= 1 && variable.var <= 3)) return false;
                } else if (opcode != Opcodes.DUP && opcode != Opcodes.IADD && opcode != Opcodes.ISUB && opcode != Opcodes.IMUL
                    && opcode != Opcodes.ARETURN && opcode != Opcodes.ICONST_0 && opcode != Opcodes.ICONST_1) return false;
            }
        }
        return true;
    }

    static MethodHandle helper() throws ReflectiveOperationException {
        Model current = model;
        if (current == null || current.helper == null) return null;
        var method = Biome.class.getDeclaredMethod(current.helper, BlockPos.class);
        method.setAccessible(true);
        return MethodHandles.lookup().unreflect(method).asType(MethodType.methodType(BlockPos.class, Biome.class, BlockPos.class));
    }

    private static String configuration(MethodNode helper, String position) {
        if (helper == null || !helper.tryCatchBlocks.isEmpty()) return null;
        var code = code(helper);
        if (code.size() < 5 || !variable(code.get(0), Opcodes.ALOAD, 1) || !(code.get(1) instanceof MethodInsnNode read)
            || read.getOpcode() != Opcodes.INVOKESTATIC || !read.desc.startsWith("()L")) return null;
        ClassNode owner = read(read.owner);
        MethodNode getter = owner == null ? null : find(owner, read.name, read.desc);
        if (getter == null) return null;
        var getterCode = code(getter);
        if (getterCode.size() != 2 || getterCode.get(0).getOpcode() != Opcodes.GETSTATIC || getterCode.get(1).getOpcode() != Opcodes.ARETURN) return null;
        for (int i = 2; i < code.size() - 2; i++) if (!(code.get(i) instanceof FieldInsnNode field) || field.getOpcode() != Opcodes.GETFIELD) return null;
        if (!(code.get(code.size() - 2) instanceof MethodInsnNode move) || move.getOpcode() != Opcodes.INVOKEVIRTUAL
            || !move.owner.equals(position.substring(1, position.length() - 1)) || !move.desc.equals("(I)" + position)
            || code.get(code.size() - 1).getOpcode() != Opcodes.ARETURN) return null;
        return read.owner;
    }

    private static String argument(String descriptor) {
        return org.objectweb.asm.Type.getArgumentTypes(descriptor)[0].getDescriptor();
    }

    private static MethodNode target(ClassNode type, String invoker) {
        for (MethodNode method : type.methods) {
            if (method.name.equals(invoker)) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call && call.owner.equals(type.name)) return find(type, call.name, call.desc);
                }
            }
        }
        return null;
    }

    private static MethodNode find(ClassNode type, String name, String descriptor) {
        for (MethodNode method : type.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
        return null;
    }

    private static ClassNode read(String name) {
        try (var stream = SurfaceTemperatureTransforms.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (stream == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static ArrayList<AbstractInsnNode> code(MethodNode method) {
        var result = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) if (instruction.getOpcode() >= 0) result.add(instruction);
        return result;
    }

    private static boolean variable(AbstractInsnNode instruction, int opcode, int index) {
        return instruction instanceof VarInsnNode variable && variable.getOpcode() == opcode && variable.var == index;
    }

    private static List<Object> fingerprint(MethodNode method, int start) {
        if (!method.tryCatchBlocks.isEmpty()) return List.of(method);
        var code = code(method);
        var result = new ArrayList<Object>();
        for (int i = start; i < code.size(); i++) {
            var instruction = code.get(i);
            result.add(instruction.getOpcode());
            if (instruction instanceof VarInsnNode variable) result.add(variable.var);
            else if (instruction instanceof FieldInsnNode field) result.add(List.of(field.owner, field.name, field.desc));
            else if (instruction instanceof MethodInsnNode call) result.add(List.of(call.owner, call.name, call.desc, call.itf));
            else if (instruction instanceof IntInsnNode value) result.add(value.operand);
            else if (instruction instanceof LdcInsnNode value) result.add(value.cst);
            else if (instruction instanceof TypeInsnNode type) result.add(type.desc);
            else if (instruction instanceof IincInsnNode increment) result.add(List.of(increment.var, increment.incr));
            else if (instruction instanceof JumpInsnNode jump) {
                var destination = (AbstractInsnNode) jump.label;
                while (destination != null && destination.getOpcode() < 0) destination = destination.getNext();
                result.add(code.indexOf(destination) - start);
            } else if (instruction instanceof InvokeDynamicInsnNode || instruction instanceof TableSwitchInsnNode || instruction instanceof LookupSwitchInsnNode
                || instruction instanceof MultiANewArrayInsnNode) return List.of(method);
        }
        return result;
    }
}
