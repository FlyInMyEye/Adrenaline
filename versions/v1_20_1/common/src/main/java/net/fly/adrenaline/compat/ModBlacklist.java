package net.fly.adrenaline.compat;

import java.util.List;
import java.util.Objects;
import net.fly.adrenaline.AdrenalinePlatform;
import net.fly.adrenaline.AdrenalinePlatform.ModInfo;
import net.fly.adrenaline.GlobalCommon;
import net.fly.adrenaline.config.AdrenalineConfig;
import net.fly.adrenaline.config.AdrenalineConfig.Data;

public final class ModBlacklist {
    private static final List<String> MOD_IDS = List.of("zfastnoise", "c2me", "terraingraph", "byepregen", "lightload");
    private static List<ModInfo> detected = List.of();
    private static List<String> detectedIds = List.of();

    private ModBlacklist() {
    }

    public static void initialize() {
        detected = MOD_IDS.stream().map(AdrenalinePlatform::modInfo).filter(Objects::nonNull).toList();
        detectedIds = detected.stream().map(ModInfo::id).sorted().toList();
        if (!detected.isEmpty()) {
            GlobalCommon.LOGGER.warn("Blacklisted mod(s) detected: {}. Make sure to remove them!", String.join(", ", detectedIds));
        }
    }

    public static List<ModInfo> detected() {
        return detected;
    }

    public static boolean shouldWarn() {
        List<String> dismissed = AdrenalineConfig.get().dismissedBlacklistedMods;
        return !detectedIds.isEmpty() && !(dismissed.containsAll(detectedIds) && detectedIds.containsAll(dismissed));
    }

    public static void dismiss() {
        Data settings = AdrenalineConfig.copy();
        settings.dismissedBlacklistedMods = detectedIds;
        AdrenalineConfig.save(settings);
    }
}
