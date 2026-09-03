package net.fly.adrenaline.natives;

import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class NativeHardwareInfo {

    private static final String CPU = findCpu();

    private NativeHardwareInfo() {
    }

    public static List<String> lines() {
        Runtime runtime = Runtime.getRuntime();
        return List.of(
            "Platform: " + NativeHardwareInfo.os() + " | " + AdrenalineNatives.platformDescription(),
            "CPU: " + CPU + " | " + runtime.availableProcessors() + " logical processors",
            "Memory: " + NativeHardwareInfo.memory(),
            "JVM: " + System.getProperty("java.vm.name", "Unknown") + " " + System.getProperty("java.version", "Unknown") + " (" + System.getProperty("java.vendor", "Unknown") + ")",
            "Native backend: " + AdrenalineNatives.backendDescription()
        );
    }

    private static String os() {
        String name = System.getProperty("os.name", "Unknown");
        String version = System.getProperty("os.version", "");
        return version.isBlank() ? name : name + " " + version;
    }

    private static String memory() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof OperatingSystemMXBean system) {
            long bytes = system.getTotalMemorySize();
            if (bytes > 0L) {
                return String.format(Locale.ROOT, "%.1f GiB", bytes / 1_073_741_824.0D);
            }
        }
        return "Unknown";
    }

    private static String findCpu() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            String cpu = readLinuxCpu();
            if (cpu != null) {
                return cpu;
            }
        }
        if (os.contains("win")) {
            String cpu = System.getenv("PROCESSOR_IDENTIFIER");
            if (cpu != null && !cpu.isBlank()) {
                return cpu;
            }
        }
        return System.getProperty("os.arch", "Unknown");
    }

    private static String readLinuxCpu() {
        try (Stream<String> lines = Files.lines(Path.of("/proc/cpuinfo"))) {
            return lines
                .map(String::strip)
                .filter(line -> line.startsWith("model name") || line.startsWith("Hardware"))
                .map(NativeHardwareInfo::value)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
        } catch (IOException | SecurityException ignored) {
            return null;
        }
    }

    private static String value(String line) {
        int separator = line.indexOf(':');
        return separator < 0 ? line : line.substring(separator + 1).strip();
    }
}
