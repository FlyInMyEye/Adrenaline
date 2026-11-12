package net.fly.adrenaline.worldgen;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.natives.NativeDensitySampler;
import net.minecraft.world.level.levelgen.NoiseChunk;

public final class NativeSliceLattice {

    private static final ThreadLocal<Workspace> WORKSPACE = ThreadLocal.withInitial(Workspace::new);

    private NativeSliceLattice() {
    }

    public static double[] evaluate(NativeDensityProgram program, NoiseChunk owner, PerlinSectionCache sections,
                                    int x, int y, int z, int width, int height, int stepXz, int stepY) {
        long count = (long) width * width * height;
        if (!AdrenalineConfig.nativeDensityEvaluationEnabled() || !AdrenalineConfig.nativePerlinBatchingEnabled()
            || !AdrenalineConfig.nativeBlendedNoiseEnabled() || count <= 0 || count > 1280) {
            return null;
        }
        Workspace workspace = WORKSPACE.get();
        if (workspace.busy) {
            return null;
        }
        workspace.busy = true;
        try {
            int size = (int) count;
            int bytes = Math.multiplyExact(program.inputCount(), size * Double.BYTES);
            if (workspace.inputs == null || workspace.inputs.capacity() < bytes) {
                workspace.inputs = ByteBuffer.allocateDirect(Math.max(Double.BYTES, bytes)).order(ByteOrder.LITTLE_ENDIAN);
            }
            if (workspace.scratch == null || workspace.scratch.length != size) {
                workspace.scratch = new double[size];
            }
            if (!program.prepareLattice(owner, sections, x, z, width, height, stepXz, workspace.inputs, workspace.scratch)) {
                return null;
            }
            double[] values = new double[size];
            return NativeDensitySampler.evaluateLattice(program, workspace.inputs, x, y, z, width, height, stepXz, stepY, values) ? values : null;
        } finally {
            workspace.busy = false;
        }
    }

    private static final class Workspace {
        private ByteBuffer inputs;
        private double[] scratch;
        private boolean busy;
    }
}
