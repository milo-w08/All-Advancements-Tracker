package dev.milow.aaigttracker.client.timer;

import net.minecraft.client.Minecraft;

public final class TimerSource {
    public TimerReading read(Minecraft minecraft) {
        if (minecraft != null && minecraft.getSingleplayerServer() == null && minecraft.getConnection() != null) {
            return TimerReading.multiplayerDisabled();
        }

        Long levelMillis = minecraft != null && minecraft.level != null
                ? Math.max(0L, minecraft.level.getGameTime()) * 50L
                : null;

        if (levelMillis != null) {
            return new TimerReading(true, levelMillis, "screen.aaigttracker.timer.level");
        }

        return TimerReading.unavailable();
    }
}
