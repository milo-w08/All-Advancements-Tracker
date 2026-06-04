package dev.milow.aaigttracker.client.timer;

public record TimerReading(boolean available, long millis, String sourceName) {
    public static TimerReading unavailable() {
        return new TimerReading(false, 0L, "screen.aaigttracker.timer.unavailable");
    }

    public static TimerReading multiplayerDisabled() {
        return new TimerReading(false, 0L, "screen.aaigttracker.timer.multiplayer_disabled");
    }
}
