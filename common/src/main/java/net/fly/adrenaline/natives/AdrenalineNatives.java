package net.fly.adrenaline.natives;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.fly.adrenaline.GlobalCommon;

public final class AdrenalineNatives {
    private static final int CAPABILITY_AVX2 = 1;
    private static final int CAPABILITY_NEON = 2;
    private static boolean initialized;
    private static boolean available;
    private static String backendDescription = "Unavailable";

    private AdrenalineNatives() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        initialized = true;
        NativeLibrary library = NativeLibrary.current();
        GlobalCommon.LOGGER.info("Getting operating system and architecture...");
        GlobalCommon.LOGGER.info("Native platform: {}", NativeLibrary.platformName());
        if (library == null) {
            GlobalCommon.LOGGER.info("No bundled native backend is available for this platform");
            return;
        }

        try (InputStream input = AdrenalineNatives.class.getResourceAsStream(library.resourcePath())) {
            if (input == null) {
                GlobalCommon.LOGGER.info("Adrenaline native library {} is not bundled", library.resourcePath());
                return;
            }

            Path extracted = Files.createTempFile("adrenaline-native-", library.fileSuffix());
            Files.copy(input, extracted, StandardCopyOption.REPLACE_EXISTING);
            extracted.toFile().deleteOnExit();
            System.load(extracted.toAbsolutePath().toString());
            int capabilities = capabilities0();
            available = true;
            if (library.x86_64()) {
                GlobalCommon.LOGGER.info("Testing AVX2... {}", (capabilities & CAPABILITY_AVX2) != 0 ? "Success" : "Unavailable");
                backendDescription = library.platform() + ((capabilities & CAPABILITY_AVX2) != 0 ? " (AVX2)" : " (scalar)");
            } else if (library.aarch64()) {
                GlobalCommon.LOGGER.info("Testing NEON... {}", (capabilities & CAPABILITY_NEON) != 0 ? "Success" : "Unavailable");
                backendDescription = library.platform() + ((capabilities & CAPABILITY_NEON) != 0 ? " (NEON)" : " (scalar)");
            } else {
                backendDescription = library.platform();
            }
            GlobalCommon.LOGGER.info("Loaded Adrenaline native backend: {}", library.platform());
        } catch (IOException | SecurityException | UnsatisfiedLinkError exception) {
            GlobalCommon.LOGGER.warn("Unable to load Adrenaline native library for {}", library.platform(), exception);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String backendDescription() {
        return backendDescription;
    }

    public static String platformDescription() {
        return NativeLibrary.platformName();
    }

    private static native int capabilities0();

    private record NativeLibrary(String platform, String resourceName, String fileSuffix, boolean x86_64, boolean aarch64) {
        private String resourcePath() {
            return "/adrenaline/natives/" + this.platform + "/" + this.resourceName;
        }

        private static NativeLibrary current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            boolean x86_64 = architecture.equals("amd64") || architecture.equals("x86_64");
            boolean arm64 = architecture.equals("aarch64") || architecture.equals("arm64");

            if (os.contains("win") && x86_64) {
                return new NativeLibrary("windows-x86_64", "adrenaline_native.dll", ".dll", true, false);
            }
            if (os.contains("win") && arm64) {
                return new NativeLibrary("windows-aarch64", "adrenaline_native.dll", ".dll", false, true);
            }
            if (os.contains("mac") || os.contains("darwin")) {
                if (x86_64) {
                    return new NativeLibrary("macos-x86_64", "libadrenaline_native.dylib", ".dylib", true, false);
                }
                if (arm64) {
                    return new NativeLibrary("macos-aarch64", "libadrenaline_native.dylib", ".dylib", false, true);
                }
            }
            if (os.contains("linux") && x86_64) {
                return new NativeLibrary("linux-x86_64", "libadrenaline_native.so", ".so", true, false);
            }
            if (os.contains("linux") && arm64) {
                return new NativeLibrary("linux-aarch64", "libadrenaline_native.so", ".so", false, true);
            }
            return null;
        }

        private static String platformName() {
            String os = System.getProperty("os.name", "Unknown");
            String architecture = System.getProperty("os.arch", "Unknown");
            String normalizedArchitecture = architecture.equalsIgnoreCase("amd64") || architecture.equalsIgnoreCase("x86_64") ? "x64"
                : architecture.equalsIgnoreCase("aarch64") || architecture.equalsIgnoreCase("arm64") ? "ARM64" : architecture;
            return os + " " + normalizedArchitecture;
        }
    }
}
