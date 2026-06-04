package dev.milow.aaigttracker.client.model;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

public record WorldKey(String value) {
    public static WorldKey fromMinecraft(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }

        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            return fromServer(server);
        }

        return fromMultiplayer(minecraft);
    }

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

    private static WorldKey fromMultiplayer(Minecraft minecraft) {
        if (minecraft.getConnection() == null || minecraft.player == null) {
            return null;
        }

        File gameDirectory = minecraft.gameDirectory;
        if (gameDirectory == null) {
            return null;
        }

        String serverId = multiplayerServerId(minecraft);
        String playerId = minecraft.player.getUUID().toString();
        Path serverPath = gameDirectory.toPath()
                .resolve("aaigttracker")
                .resolve("servers")
                .resolve(sanitize(serverId))
                .resolve(playerId);
        return new WorldKey(serverPath.toAbsolutePath().normalize().toString());
    }

    private static String multiplayerServerId(Minecraft minecraft) {
        ServerData serverData = minecraft.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            return serverData.ip;
        }

        if (minecraft.getConnection() != null && minecraft.getConnection().getConnection() != null) {
            return String.valueOf(minecraft.getConnection().getConnection().getRemoteAddress());
        }

        return "unknown_server";
    }

    private static String sanitize(String value) {
        String normalized = value == null || value.isBlank()
                ? "unknown"
                : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }

    private static Path safeWorldPath(IntegratedServer server) {
        try {
            return server.getWorldPath(LevelResource.ROOT);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
