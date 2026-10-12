package net.fly.adrenaline.scheduler;

final class AdaptiveWorkerLimit {

    private int configuredWorkers;
    private int workers;
    private int healthySamples;
    private long lastReductionTick = Long.MIN_VALUE;

    void reset(int configuredWorkers) {
        this.configuredWorkers = Math.max(1, configuredWorkers);
        this.workers = this.configuredWorkers;
        this.healthySamples = 0;
        this.lastReductionTick = Long.MIN_VALUE;
    }

    int update(int configuredWorkers, boolean enabled, double cpuLoad, double tickMillis, long tick) {
        if (this.configuredWorkers != Math.max(1, configuredWorkers) || !enabled) {
            this.reset(configuredWorkers);
        }
        if (!enabled) {
            return this.workers;
        }
        boolean cpuAvailable = Double.isFinite(cpuLoad) && cpuLoad >= 0.0D && cpuLoad <= 1.0D;
        boolean ticksAvailable = Double.isFinite(tickMillis) && tickMillis > 0.0D;
        if (cpuAvailable && cpuLoad > 0.90D || ticksAvailable && tickMillis > 50.0D) {
            if (this.lastReductionTick != tick) {
                int minimum = this.configuredWorkers / 2 + this.configuredWorkers % 2;
                this.workers = Math.max(minimum, this.workers - 1);
                this.lastReductionTick = tick;
            }
            this.healthySamples = 0;
        } else if ((!cpuAvailable || cpuLoad < 0.80D) && (!ticksAvailable || tickMillis < 40.0D)) {
            if (++this.healthySamples >= 3) {
                this.workers = Math.min(this.configuredWorkers, this.workers + 1);
                this.healthySamples = 0;
            }
        } else {
            this.healthySamples = 0;
        }
        return this.workers;
    }
}
