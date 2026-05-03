package dev.milow.aaigttracker.client;

public record TimerReading(
        boolean available,
        long millis,
        String sourceName,
        Long speedRunIgtMillis,
        Long fallbackLevelMillis
) {
    public static TimerReading unavailable() {
        return new TimerReading(false, 0L, "screen.aaigttracker.timer.unavailable", null, null);
    }

    public boolean hasSpeedRunIgtMillis() {
        return this.speedRunIgtMillis != null;
    }

    public boolean hasFallbackLevelMillis() {
        return this.fallbackLevelMillis != null;
    }

    public TimerReading withDisplay(boolean available, long millis, String sourceName) {
        return new TimerReading(available, millis, sourceName, this.speedRunIgtMillis, this.fallbackLevelMillis);
    }
}
