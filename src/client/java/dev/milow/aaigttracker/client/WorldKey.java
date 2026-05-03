package dev.milow.aaigttracker.client;

import java.nio.file.Path;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

public record WorldKey(String value) {
    public static WorldKey fromServer(IntegratedServer server) {
        if (server == null) {
            return null;
        }

        Path worldPath = safeWorldPath(server);
        if (worldPath == null) {
            worldPath = server.getServerDirectory();
        }

        if (worldPath == null) {
            return null;
        }

        return new WorldKey(worldPath.toAbsolutePath().normalize().toString());
    }

    private static Path safeWorldPath(IntegratedServer server) {
        try {
            return server.getWorldPath(LevelResource.ROOT);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
