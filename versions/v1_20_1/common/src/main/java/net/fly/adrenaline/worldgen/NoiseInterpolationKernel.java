package net.fly.adrenaline.worldgen;

public final class NoiseInterpolationKernel {

    private NoiseInterpolationKernel() {
    }

    public static void interpolateY(double delta, double[] noise000, double[] noise001, double[] noise100, double[] noise101, double[] noise010, double[] noise011, double[] noise110, double[] noise111, double[] valueXZ00, double[] valueXZ01, double[] valueXZ10, double[] valueXZ11) {
        for (int index = 0; index < valueXZ00.length; index++) {
            valueXZ00[index] = noise000[index] + delta * (noise010[index] - noise000[index]);
            valueXZ10[index] = noise100[index] + delta * (noise110[index] - noise100[index]);
            valueXZ01[index] = noise001[index] + delta * (noise011[index] - noise001[index]);
            valueXZ11[index] = noise101[index] + delta * (noise111[index] - noise101[index]);
        }
    }

    public static void interpolateX(double delta, double[] valueXZ00, double[] valueXZ01, double[] valueXZ10, double[] valueXZ11, double[] valueZ0, double[] valueZ1) {
        for (int index = 0; index < valueZ0.length; index++) {
            valueZ0[index] = valueXZ00[index] + delta * (valueXZ10[index] - valueXZ00[index]);
            valueZ1[index] = valueXZ01[index] + delta * (valueXZ11[index] - valueXZ01[index]);
        }
    }

    public static void interpolateZ(double delta, double[] valueZ0, double[] valueZ1, double[] values) {
        for (int index = 0; index < values.length; index++) {
            values[index] = valueZ0[index] + delta * (valueZ1[index] - valueZ0[index]);
        }
    }
}
