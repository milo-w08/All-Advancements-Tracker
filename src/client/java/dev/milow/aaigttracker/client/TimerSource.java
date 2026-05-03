package dev.milow.aaigttracker.client;

import net.minecraft.client.Minecraft;

public final class TimerSource {
    private final SpeedRunIgtBridge speedRunIgtBridge;

    public TimerSource() {
        this(new SpeedRunIgtBridge());
    }

    public TimerSource(SpeedRunIgtBridge speedRunIgtBridge) {
        this.speedRunIgtBridge = speedRunIgtBridge;
    }

    public TimerReading read(Minecraft minecraft) {
        long speedRunIgtMillis = this.speedRunIgtBridge.readMillis();
        Long resolvedSpeedRunIgtMillis = speedRunIgtMillis >= 0L ? speedRunIgtMillis : null;
        Long fallbackLevelMillis = minecraft.level != null
                ? Math.max(0L, minecraft.level.getGameTime()) * 50L
                : null;

        if (resolvedSpeedRunIgtMillis != null) {
            return new TimerReading(
                    true,
                    resolvedSpeedRunIgtMillis,
                    "screen.aaigttracker.timer.speedrunigt",
                    resolvedSpeedRunIgtMillis,
                    fallbackLevelMillis
            );
        }

        if (fallbackLevelMillis != null) {
            return new TimerReading(
                    true,
                    fallbackLevelMillis,
                    "screen.aaigttracker.timer.fallback",
                    null,
                    fallbackLevelMillis
            );
        }

        return TimerReading.unavailable();
    }
}
