package net.fly.adrenaline.util;

import com.mojang.math.OctahedralGroup;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;

public final class WorldgenWarmup {

    private static final AtomicBoolean OCTAHEDRAL_GROUPS_WARMED = new AtomicBoolean();

    private WorldgenWarmup() {
    }

    public static void warmSharedCaches() {
        warmOctahedralGroups();
    }

    private static void warmOctahedralGroups() {
        if (!OCTAHEDRAL_GROUPS_WARMED.compareAndSet(false, true)) {
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
    }
}
