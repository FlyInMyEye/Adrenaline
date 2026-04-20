package net.fly.adrenaline.util;

import net.fly.adrenaline.Adrenaline;

public final class ClassPreloader {

    private static final String[] CLASS_NAMES = {
        "net.minecraft.util.random.WeightedEntry$IntrusiveBase",
        "net.minecraftforge.common.DungeonHooks$DungeonMob",
        "net.minecraft.world.entity.AgeableMob$AgeableMobGroupData",
        "com.teamabnormals.blueprint.core.api.BlueprintRabbitVariants$BlueprintRabbitGroupData"
    };

    private ClassPreloader() {
    }

    public static void preloadKnownProblematicClasses() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String className : CLASS_NAMES) {
            try {
                Class.forName(className, true, classLoader);
            } catch (ClassNotFoundException exception) {
                Adrenaline.LOGGER.debug("Skipping unavailable class preload {}", className);
            } catch (LinkageError exception) {
                Adrenaline.LOGGER.debug("Skipping linked class preload {}", className);
            }
        }
    }
}
