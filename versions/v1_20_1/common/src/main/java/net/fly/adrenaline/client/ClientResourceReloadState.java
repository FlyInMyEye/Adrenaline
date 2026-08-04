package net.fly.adrenaline.client;

public final class ClientResourceReloadState {
    private static volatile boolean ready = true;

    private ClientResourceReloadState() {
    }

    public static boolean isReady() {
        return ready;
    }

    public static void setReady(boolean ready) {
        ClientResourceReloadState.ready = ready;
    }
}
