package net.fly.adrenaline.util;

public final class GenerationRequestTiming {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private GenerationRequestTiming() {
    }

    public static Long enter(long startedNanos) {
        Long previous = CURRENT.get();
        CURRENT.set(startedNanos);
        return previous;
    }

    public static void restore(Long previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    public static long startedNanos() {
        Long current = CURRENT.get();
        return current == null ? 0L : current;
    }
}
