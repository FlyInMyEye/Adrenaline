package net.fly.adrenaline.compatdata;

public record IncompatibleData(String modId, String[] incompatibleMixins, ICrashController controller) {
}
