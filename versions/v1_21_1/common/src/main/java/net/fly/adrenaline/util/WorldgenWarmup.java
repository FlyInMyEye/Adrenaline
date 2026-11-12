package net.fly.adrenaline.util;

import com.mojang.math.OctahedralGroup;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;

public final class WorldgenWarmup {

    private static boolean octahedralGroupsWarmed;

    private WorldgenWarmup() {
    }

    public static synchronized void warmSharedCaches() {
        if (octahedralGroupsWarmed) {
            return;
        }

        for (OctahedralGroup group : OctahedralGroup.values()) {
            for (Direction direction : Direction.values()) {
                group.rotate(direction);
            }
            for (FrontAndTop frontAndTop : FrontAndTop.values()) {
                group.rotate(frontAndTop);
            }
        }
        octahedralGroupsWarmed = true;
    }
}
