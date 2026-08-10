package net.fly.adrenaline.worldgen;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class CellDensityCompiler implements Opcodes {

    private static final String GENERATED_NAME = "net/fly/adrenaline/worldgen/GeneratedCellDensityEvaluator";
    private static final String EVALUATOR_NAME = Type.getInternalName(CellDensityEvaluator.class);
    private static final String DENSITY_FUNCTION_NAME = Type.getInternalName(DensityFunction.class);
    private static final String CONTEXT_NAME = Type.getInternalName(DensityFunction.FunctionContext.class);
    private static final String PROVIDER_NAME = Type.getInternalName(DensityFunction.ContextProvider.class);
    private static final String INTERPOLATOR_NAME = Type.getInternalName(AdrenalineCompiledInterpolatorAccess.class);
    private static final String BEARDIFIER_NAME = Type.getInternalName(AdrenalineCompiledBeardifierAccess.class);
    private static final String BRIDGE_NAME = Type.getInternalName(DensityEvaluationBridge.class);
    private static final String LEAVES_DESCRIPTOR = "[L" + DENSITY_FUNCTION_NAME + ";";
    private static final int MAX_OPERATORS = 256;
    private static final int MAX_GRAPH_SHAPES = 64;
    private static final ConcurrentMap<String, Constructor<?>> CONSTRUCTORS = new ConcurrentHashMap<>();

    private CellDensityCompiler() {
    }

    public static CellDensityEvaluator compile(DensityFunction root) {
        try {
            Generator generator = new Generator();
            byte[] bytecode = generator.generate(root);
            if (bytecode == null) {
                return null;
            }
            String key = generator.key();
            Constructor<?> constructor = CONSTRUCTORS.get(key);
            if (constructor == null) {
                if (CONSTRUCTORS.size() >= MAX_GRAPH_SHAPES) {
                    return null;
                }
                constructor = CONSTRUCTORS.computeIfAbsent(key, ignored -> defineConstructor(bytecode));
                if (constructor == null) {
                    return null;
                }
            }
            return (CellDensityEvaluator) constructor.newInstance((Object) generator.leaves());
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static Constructor<?> defineConstructor(byte[] bytecode) {
        try {
            Class<?> generatedClass = MethodHandles.lookup()
                .defineHiddenClass(bytecode, true, MethodHandles.Lookup.ClassOption.NESTMATE)
                .lookupClass();
            return generatedClass.getDeclaredConstructor(DensityFunction[].class);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static int operationOrdinal(Object function) throws ReflectiveOperationException {
        RecordComponent[] components = function.getClass().getRecordComponents();
        if (components == null) {
            throw new IllegalArgumentException();
        }
        for (RecordComponent component : components) {
            if (component.getType().isEnum()) {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                return ((Enum<?>) accessor.invoke(function)).ordinal();
            }
        }
        throw new IllegalArgumentException();
    }

    private static final class Generator {

        private final List<DensityFunction> leaves = new ArrayList<>();
        private final Map<DensityFunction, Integer> leafIndices = new IdentityHashMap<>();
        private final StringBuilder key = new StringBuilder();
        private MethodVisitor method;
        private int nextLocal = 5;
        private int operators;

        private byte[] generate(DensityFunction root) throws ReflectiveOperationException {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            writer.visit(V17, ACC_FINAL | ACC_SUPER, GENERATED_NAME, null, "java/lang/Object", new String[]{EVALUATOR_NAME});
            writer.visitField(ACC_PRIVATE | ACC_FINAL, "leaves", LEAVES_DESCRIPTOR, null, null).visitEnd();
            this.writeConstructor(writer);
            this.writeFillMethod(writer, root);
            if (this.operators == 0 || this.operators > MAX_OPERATORS) {
                return null;
            }
            writer.visitEnd();
            return writer.toByteArray();
        }

        private DensityFunction[] leaves() {
            return this.leaves.toArray(DensityFunction[]::new);
        }

        private String key() {
            return this.key.toString();
        }

        private void writeConstructor(ClassWriter writer) {
            MethodVisitor constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "(" + LEAVES_DESCRIPTOR + ")V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            constructor.visitVarInsn(ALOAD, 0);
            constructor.visitVarInsn(ALOAD, 1);
            constructor.visitFieldInsn(PUTFIELD, GENERATED_NAME, "leaves", LEAVES_DESCRIPTOR);
            constructor.visitInsn(RETURN);
            constructor.visitMaxs(0, 0);
            constructor.visitEnd();
        }

        private void writeFillMethod(ClassWriter writer, DensityFunction root) throws ReflectiveOperationException {
            this.method = writer.visitMethod(ACC_PUBLIC, "fill", "([DL" + PROVIDER_NAME + ";)V", null, null);
            this.method.visitCode();
            this.method.visitInsn(ICONST_0);
            this.method.visitVarInsn(ISTORE, 3);
            Label loop = new Label();
            Label end = new Label();
            this.method.visitLabel(loop);
            this.method.visitVarInsn(ILOAD, 3);
            this.method.visitVarInsn(ALOAD, 1);
            this.method.visitInsn(ARRAYLENGTH);
            this.method.visitJumpInsn(IF_ICMPGE, end);
            this.method.visitVarInsn(ALOAD, 2);
            this.method.visitVarInsn(ILOAD, 3);
            this.method.visitMethodInsn(INVOKESTATIC, BRIDGE_NAME, "forIndex", "(L" + PROVIDER_NAME + ";I)L" + CONTEXT_NAME + ";", false);
            this.method.visitVarInsn(ASTORE, 4);
            this.method.visitVarInsn(ALOAD, 1);
            this.method.visitVarInsn(ILOAD, 3);
            this.emit(root, 0);
            this.method.visitInsn(DASTORE);
            this.method.visitIincInsn(3, 1);
            this.method.visitJumpInsn(GOTO, loop);
            this.method.visitLabel(end);
            this.method.visitInsn(RETURN);
            this.method.visitMaxs(0, 0);
            this.method.visitEnd();
        }

        private void emit(DensityFunction function, int depth) throws ReflectiveOperationException {
            if (depth > MAX_OPERATORS || this.operators > MAX_OPERATORS) {
                throw new IllegalArgumentException();
            }
            if (function instanceof AdrenalineClampFunctionAccess clamp) {
                this.operators++;
                this.key.append('C');
                this.append(clamp.adrenaline$clampMin());
                this.append(clamp.adrenaline$clampMax());
                this.key.append('{');
                this.emit(clamp.adrenaline$clampInput(), depth + 1);
                this.key.append('}');
                this.push(clamp.adrenaline$clampMin());
                this.push(clamp.adrenaline$clampMax());
                this.method.visitMethodInsn(INVOKESTATIC, BRIDGE_NAME, "clamp", "(DDD)D", false);
                return;
            }
            if (function instanceof AdrenalineMappedFunctionAccess mapped) {
                this.operators++;
                int operation = operationOrdinal(mapped);
                this.key.append('U').append(operation).append('{');
                this.emitMapped(mapped, operation, depth + 1);
                this.key.append('}');
                return;
            }
            if (function instanceof AdrenalineMulOrAddAccess transform) {
                this.operators++;
                int operation = operationOrdinal(transform);
                this.key.append('S').append(operation);
                this.append(transform.adrenaline$transformArgument());
                this.key.append('{');
                this.emit(transform.adrenaline$transformInput(), depth + 1);
                this.key.append('}');
                this.push(transform.adrenaline$transformArgument());
                this.method.visitInsn(operation == 0 ? DMUL : DADD);
                return;
            }
            if (function instanceof AdrenalineBinaryFunctionAccess binary) {
                this.operators++;
                int operation = operationOrdinal(function);
                this.key.append('B').append(operation);
                if (operation == 2) {
                    this.append(binary.adrenaline$secondArgument().minValue());
                } else if (operation == 3) {
                    this.append(binary.adrenaline$secondArgument().maxValue());
                }
                this.key.append('{');
                this.emitBinary(binary, operation, depth + 1);
                this.key.append('}');
                return;
            }
            if (function instanceof AdrenalineRangeChoiceAccess range) {
                this.operators++;
                this.key.append('R');
                this.append(range.adrenaline$minInclusive());
                this.append(range.adrenaline$maxExclusive());
                this.key.append('{');
                this.emitRange(range, depth + 1);
                this.key.append('}');
                return;
            }
            this.emitLeaf(function);
        }

        private void emitMapped(AdrenalineMappedFunctionAccess mapped, int operation, int depth) throws ReflectiveOperationException {
            this.emit(mapped.adrenaline$mappedInput(), depth);
            if (operation == 0) {
                this.method.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "abs", "(D)D", false);
                return;
            }
            int value = this.allocateDoubleLocal();
            this.method.visitVarInsn(DSTORE, value);
            if (operation == 1) {
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitInsn(DMUL);
                return;
            }
            if (operation == 2) {
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitInsn(DMUL);
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitInsn(DMUL);
                return;
            }
            if (operation == 3 || operation == 4) {
                Label negative = new Label();
                Label end = new Label();
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitInsn(DCONST_0);
                this.method.visitInsn(DCMPL);
                this.method.visitJumpInsn(IFLE, negative);
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitJumpInsn(GOTO, end);
                this.method.visitLabel(negative);
                this.method.visitVarInsn(DLOAD, value);
                this.push(operation == 3 ? 0.5D : 0.25D);
                this.method.visitInsn(DMUL);
                this.method.visitLabel(end);
                return;
            }
            if (operation == 5) {
                this.method.visitVarInsn(DLOAD, value);
                this.push(-1.0D);
                this.method.visitInsn(DCONST_1);
                this.method.visitMethodInsn(INVOKESTATIC, BRIDGE_NAME, "clamp", "(DDD)D", false);
                this.method.visitVarInsn(DSTORE, value);
                this.method.visitVarInsn(DLOAD, value);
                this.push(2.0D);
                this.method.visitInsn(DDIV);
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitInsn(DMUL);
                this.method.visitVarInsn(DLOAD, value);
                this.method.visitInsn(DMUL);
                this.push(24.0D);
                this.method.visitInsn(DDIV);
                this.method.visitInsn(DSUB);
                return;
            }
            throw new IllegalArgumentException();
        }

        private void emitBinary(AdrenalineBinaryFunctionAccess binary, int operation, int depth) throws ReflectiveOperationException {
            if (operation == 0) {
                this.emit(binary.adrenaline$firstArgument(), depth);
                this.emit(binary.adrenaline$secondArgument(), depth);
                this.method.visitInsn(DADD);
                return;
            }
            int first = this.allocateDoubleLocal();
            this.emit(binary.adrenaline$firstArgument(), depth);
            this.method.visitVarInsn(DSTORE, first);
            Label evaluateSecond = new Label();
            Label end = new Label();
            if (operation == 1) {
                this.method.visitVarInsn(DLOAD, first);
                this.method.visitInsn(DCONST_0);
                this.method.visitInsn(DCMPL);
                this.method.visitJumpInsn(IFNE, evaluateSecond);
                this.method.visitInsn(DCONST_0);
                this.method.visitJumpInsn(GOTO, end);
                this.method.visitLabel(evaluateSecond);
                this.method.visitVarInsn(DLOAD, first);
                this.emit(binary.adrenaline$secondArgument(), depth);
                this.method.visitInsn(DMUL);
                this.method.visitLabel(end);
                return;
            }
            if (operation == 2) {
                this.method.visitVarInsn(DLOAD, first);
                this.push(binary.adrenaline$secondArgument().minValue());
                this.method.visitInsn(DCMPG);
                this.method.visitJumpInsn(IFGE, evaluateSecond);
                this.method.visitVarInsn(DLOAD, first);
                this.method.visitJumpInsn(GOTO, end);
                this.method.visitLabel(evaluateSecond);
                this.method.visitVarInsn(DLOAD, first);
                this.emit(binary.adrenaline$secondArgument(), depth);
                this.method.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "min", "(DD)D", false);
                this.method.visitLabel(end);
                return;
            }
            if (operation == 3) {
                this.method.visitVarInsn(DLOAD, first);
                this.push(binary.adrenaline$secondArgument().maxValue());
                this.method.visitInsn(DCMPL);
                this.method.visitJumpInsn(IFLE, evaluateSecond);
                this.method.visitVarInsn(DLOAD, first);
                this.method.visitJumpInsn(GOTO, end);
                this.method.visitLabel(evaluateSecond);
                this.method.visitVarInsn(DLOAD, first);
                this.emit(binary.adrenaline$secondArgument(), depth);
                this.method.visitMethodInsn(INVOKESTATIC, "java/lang/Math", "max", "(DD)D", false);
                this.method.visitLabel(end);
                return;
            }
            throw new IllegalArgumentException();
        }

        private void emitRange(AdrenalineRangeChoiceAccess range, int depth) throws ReflectiveOperationException {
            int input = this.allocateDoubleLocal();
            this.emit(range.adrenaline$rangeInput(), depth);
            this.method.visitVarInsn(DSTORE, input);
            Label outOfRange = new Label();
            Label end = new Label();
            this.method.visitVarInsn(DLOAD, input);
            this.push(range.adrenaline$minInclusive());
            this.method.visitInsn(DCMPL);
            this.method.visitJumpInsn(IFLT, outOfRange);
            this.method.visitVarInsn(DLOAD, input);
            this.push(range.adrenaline$maxExclusive());
            this.method.visitInsn(DCMPG);
            this.method.visitJumpInsn(IFGE, outOfRange);
            this.emit(range.adrenaline$whenInRange(), depth);
            this.method.visitJumpInsn(GOTO, end);
            this.method.visitLabel(outOfRange);
            this.emit(range.adrenaline$whenOutOfRange(), depth);
            this.method.visitLabel(end);
        }

        private void emitLeaf(DensityFunction function) {
            int index = this.leafIndices.computeIfAbsent(function, key -> {
                int next = this.leaves.size();
                this.leaves.add(key);
                return next;
            });
            int mode = function instanceof AdrenalineCompiledInterpolatorAccess
                ? 1
                : function instanceof AdrenalineCompiledBeardifierAccess && AdrenalineConfig.beardifierOptimizationsEnabled() ? 2 : 0;
            this.key.append('L').append(mode).append(':').append(index).append(';');
            this.method.visitVarInsn(ALOAD, 0);
            this.method.visitFieldInsn(GETFIELD, GENERATED_NAME, "leaves", LEAVES_DESCRIPTOR);
            this.push(index);
            this.method.visitInsn(AALOAD);
            if (function instanceof AdrenalineCompiledInterpolatorAccess) {
                this.method.visitTypeInsn(CHECKCAST, INTERPOLATOR_NAME);
                this.method.visitVarInsn(ALOAD, 4);
                this.method.visitMethodInsn(INVOKEINTERFACE, INTERPOLATOR_NAME, "adrenaline$computeCellValue", "(L" + CONTEXT_NAME + ";)D", true);
                return;
            }
            if (function instanceof AdrenalineCompiledBeardifierAccess && AdrenalineConfig.beardifierOptimizationsEnabled()) {
                this.method.visitTypeInsn(CHECKCAST, BEARDIFIER_NAME);
                this.method.visitVarInsn(ALOAD, 4);
                this.method.visitMethodInsn(INVOKEINTERFACE, BEARDIFIER_NAME, "adrenaline$computeDirect", "(L" + CONTEXT_NAME + ";)D", true);
                return;
            }
            this.method.visitVarInsn(ALOAD, 4);
            this.method.visitMethodInsn(INVOKESTATIC, BRIDGE_NAME, "compute", "(L" + DENSITY_FUNCTION_NAME + ";L" + CONTEXT_NAME + ";)D", false);
        }

        private int allocateDoubleLocal() {
            int local = this.nextLocal;
            this.nextLocal += 2;
            return local;
        }

        private void push(int value) {
            this.method.visitLdcInsn(value);
        }

        private void push(double value) {
            this.method.visitLdcInsn(value);
        }

        private void append(double value) {
            this.key.append(':').append(Long.toHexString(Double.doubleToRawLongBits(value)));
        }
    }
}
