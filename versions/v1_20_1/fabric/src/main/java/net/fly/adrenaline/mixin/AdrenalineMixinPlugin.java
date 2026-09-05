package net.fly.adrenaline.mixin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fly.adrenaline.BuildConfig;
import net.fly.adrenaline.compat.OptimizationTakeoverDetector;
import net.fly.adrenaline.compatdata.IncompatibilityRegistry;
import net.fly.adrenaline.compatdata.IncompatibleData;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

public final class AdrenalineMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> DEBUG_MIXINS = Set.of(
        "net.fly.adrenaline.mixin.MixinGui",
        "net.fly.adrenaline.mixin.MixinLoggerChunkProgressListener",
        "net.fly.adrenaline.mixin.MixinMinecraftServerWorldgenDebug",
        "net.fly.adrenaline.mixin.MixinPlayerList",
        "net.fly.adrenaline.mixin.MixinWorldgenDifferenceInput"
    );
    private static final String FLAT_CACHE_MIXIN = "net.fly.adrenaline.mixin.MixinNoiseChunkFlatCacheAllocation";
    private static final String NOISE_CHUNK_MIXIN = "net.fly.adrenaline.mixin.MixinNoiseChunk";
    private static final String FLAT_CACHE_ARRAYS = "net/fly/adrenaline/worldgen/FlatCacheArrays";

    private Set<String> disabledMixins = Set.of();

    @Override
    public void onLoad(String mixinPackage) {
        Set<String> mixins = new HashSet<>();
        for (IncompatibleData incompatibility : IncompatibilityRegistry.all()) {
            if (IncompatibilityRegistry.isLoaded(incompatibility.modId())) {
                mixins.addAll(List.of(incompatibility.incompatibleMixins()));
            }
        }
        this.disabledMixins = Set.copyOf(mixins);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return (BuildConfig.DEBUG || !DEBUG_MIXINS.contains(mixinClassName)) && !this.disabledMixins.contains(mixinClassName);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (FLAT_CACHE_MIXIN.equals(mixinClassName)) {
            rewriteFlatCacheAllocation(targetClass);
        } else if (NOISE_CHUNK_MIXIN.equals(mixinClassName)) {
            skipEmptyBlenderPopulation(targetClass);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.contains("OptimizationTakeoverDetector")) {
            OptimizationTakeoverDetector.captureClaims(targetClass, mixinInfo);
            OptimizationTakeoverDetector.detectTakeovers(targetClass, AdrenalineMixinPlugin::displayName);
        } else {
            OptimizationTakeoverDetector.captureClaims(targetClass, mixinInfo);
        }
    }

    private static String displayName(String mixinClassName) {
        String resource = mixinClassName.replace('.', '/') + ".class";
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            if (container.findPath(resource).isPresent()) {
                return container.getMetadata().getName();
            }
        }
        return mixinClassName;
    }

    private static void rewriteFlatCacheAllocation(ClassNode targetClass) {
        for (MethodNode method : targetClass.methods) {
            if (!"<init>".equals(method.name)) {
                continue;
            }

            Type[] arguments = Type.getArgumentTypes(method.desc);
            if (arguments.length != 3 || arguments[0].getSort() != Type.OBJECT || arguments[2].getSort() != Type.BOOLEAN) {
                continue;
            }

            MultiANewArrayInsnNode allocation = null;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MultiANewArrayInsnNode candidate && "[[D".equals(candidate.desc) && candidate.dims == 2) {
                    if (allocation != null) {
                        return;
                    }
                    allocation = candidate;
                }
            }
            if (allocation == null) {
                continue;
            }

            InsnList argumentsToAdd = new InsnList();
            argumentsToAdd.add(new VarInsnNode(Opcodes.ALOAD, 1));
            argumentsToAdd.add(new VarInsnNode(Opcodes.ILOAD, 3));
            method.instructions.insertBefore(allocation, argumentsToAdd);
            method.instructions.set(allocation, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                FLAT_CACHE_ARRAYS,
                "allocateFlatCacheValues",
                "(II" + arguments[0].getDescriptor() + "Z)[[D",
                false
            ));
            return;
        }
    }

    private static void skipEmptyBlenderPopulation(ClassNode targetClass) {
        for (MethodNode method : targetClass.methods) {
            if (!"<init>".equals(method.name)) {
                continue;
            }

            MethodInsnNode blendingCall = findBlendingCall(targetClass, method);
            if (blendingCall == null) {
                continue;
            }
            FieldNode blenderField = findField(targetClass, "L" + blendingCall.owner + ";");
            JumpInsnNode outerLoopExit = findOuterLoopExit(method, blendingCall);
            if (blenderField == null || outerLoopExit == null) {
                continue;
            }
            AbstractInsnNode loopStart = findLoopStart(outerLoopExit);
            if (loopStart == null) {
                continue;
            }

            LabelNode populateValues = new LabelNode();
            InsnList guard = new InsnList();
            guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
            guard.add(new FieldInsnNode(Opcodes.GETFIELD, targetClass.name, blenderField.name, blenderField.desc));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, FLAT_CACHE_ARRAYS, "isEmptyBlender", "(" + blenderField.desc + ")Z", false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ, populateValues));
            guard.add(new JumpInsnNode(Opcodes.GOTO, outerLoopExit.label));
            guard.add(populateValues);
            method.instructions.insertBefore(loopStart, guard);
            return;
        }
    }

    private static MethodInsnNode findBlendingCall(ClassNode targetClass, MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode candidate)) {
                continue;
            }
            Type[] arguments = Type.getArgumentTypes(candidate.desc);
            if (arguments.length != 2 || arguments[0].getSort() != Type.INT || arguments[1].getSort() != Type.INT
                || Type.getReturnType(candidate.desc).getSort() != Type.OBJECT) {
                continue;
            }
            if (findField(targetClass, "L" + candidate.owner + ";") != null) {
                return candidate;
            }
        }
        return null;
    }

    private static FieldNode findField(ClassNode targetClass, String descriptor) {
        for (FieldNode field : targetClass.fields) {
            if (descriptor.equals(field.desc)) {
                return field;
            }
        }
        return null;
    }

    private static JumpInsnNode findOuterLoopExit(MethodNode method, MethodInsnNode blendingCall) {
        int blendingIndex = instructionIndex(method, blendingCall);
        JumpInsnNode outerLoopExit = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof JumpInsnNode candidate) || candidate.getOpcode() != Opcodes.IF_ICMPGT) {
                continue;
            }
            int candidateIndex = instructionIndex(method, candidate);
            if (candidateIndex >= blendingIndex || instructionIndex(method, candidate.label) <= blendingIndex) {
                continue;
            }
            if (outerLoopExit == null || candidateIndex < instructionIndex(method, outerLoopExit)) {
                outerLoopExit = candidate;
            }
        }
        return outerLoopExit;
    }

    private static AbstractInsnNode findLoopStart(JumpInsnNode loopExit) {
        AbstractInsnNode loopLoad = previousInstruction(loopExit.getPrevious());
        while (loopLoad != null && (!(loopLoad instanceof VarInsnNode variable) || variable.getOpcode() != Opcodes.ILOAD)) {
            loopLoad = previousInstruction(loopLoad.getPrevious());
        }
        if (!(loopLoad instanceof VarInsnNode load)) {
            return null;
        }

        for (AbstractInsnNode instruction = previousInstruction(load.getPrevious()); instruction != null; instruction = previousInstruction(instruction.getPrevious())) {
            if (!(instruction instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ISTORE || store.var != load.var) {
                continue;
            }
            AbstractInsnNode value = previousInstruction(store.getPrevious());
            return value != null && value.getOpcode() == Opcodes.ICONST_0 ? value : null;
        }
        return null;
    }

    private static AbstractInsnNode previousInstruction(AbstractInsnNode instruction) {
        while (instruction != null && instruction.getOpcode() < 0) {
            instruction = instruction.getPrevious();
        }
        return instruction;
    }

    private static int instructionIndex(MethodNode method, AbstractInsnNode needle) {
        int index = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext(), index++) {
            if (instruction == needle) {
                return index;
            }
        }
        return -1;
    }
}
