package net.fly.adrenaline.worldgen;

import net.fly.adrenaline.natives.PerlinNativeSampler;

public interface AdrenalineNormalNoiseNativeAccess {
    PerlinNativeSampler.Data adrenaline$getFirstNativeData();

    PerlinNativeSampler.Data adrenaline$getSecondNativeData();

    double adrenaline$getNativeValueFactor();
}
