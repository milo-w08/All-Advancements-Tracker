package dev.milow.aaigttracker.client.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.milow.aaigttracker.AllAdvancementsIgtTracker;
import dev.milow.aaigttracker.client.model.WorldKey;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class WaypointService {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Map<WorldKey, List<WaypointSnapshot>> cache = new HashMap<>();

    public List<WaypointSnapshot> waypoints(WorldKey worldKey) {
        if (worldKey == null) {
            return List.of();
        }

        return this.cache.computeIfAbsent(worldKey, this::loadWaypoints);
    }

    public WaypointSnapshot createAtPlayer(WorldKey worldKey, LocalPlayer player, String rawLabel) {
        if (worldKey == null || player == null) {
            return null;
        }

        BlockPos pos = player.blockPosition();
        Identifier dimensionId = player.level().dimension().identifier();
        WaypointSnapshot waypoint = new WaypointSnapshot(
                Identifier.fromNamespaceAndPath(
                        AllAdvancementsIgtTracker.MOD_ID,
                        "waypoint_" + UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT)
                ),
                normalizeLabel(rawLabel),
                dimensionId,
                pos.getX(),
                pos.getY(),
                pos.getZ(),
                System.currentTimeMillis()
        );
        List<WaypointSnapshot> updated = new ArrayList<>(this.waypoints(worldKey));
        updated.add(waypoint);
        this.replace(worldKey, updated);
        return waypoint;
    }

    public void rename(WorldKey worldKey, Identifier waypointId, String rawLabel) {
        if (worldKey == null || waypointId == null) {
            return;
        }

        List<WaypointSnapshot> updated = new ArrayList<>(this.waypoints(worldKey).size());
        for (WaypointSnapshot waypoint : this.waypoints(worldKey)) {
            if (waypoint.id().equals(waypointId)) {
                updated.add(new WaypointSnapshot(
                        waypoint.id(),
                        normalizeLabel(rawLabel),
                        waypoint.dimensionId(),
                        waypoint.x(),
                        waypoint.y(),
                        waypoint.z(),
                        waypoint.createdAtMillis()
                ));
            } else {
                updated.add(waypoint);
            }
        }
        this.replace(worldKey, updated);
    }

    public void delete(WorldKey worldKey, Identifier waypointId) {
        if (worldKey == null || waypointId == null) {
            return;
        }

        List<WaypointSnapshot> updated = this.waypoints(worldKey).stream()
                .filter(waypoint -> !waypoint.id().equals(waypointId))
                .toList();
        this.replace(worldKey, updated);
    }

    private void replace(WorldKey worldKey, List<WaypointSnapshot> waypoints) {
        List<WaypointSnapshot> copy = List.copyOf(waypoints);
        this.cache.put(worldKey, copy);
        this.saveWaypoints(worldKey, copy);
    }

    private List<WaypointSnapshot> loadWaypoints(WorldKey worldKey) {
        Path path = this.waypointsPath(worldKey);
        if (path == null || !Files.isRegularFile(path)) {
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return List.of();
            }

            JsonArray entries = parsed.getAsJsonObject().getAsJsonArray("waypoints");
            if (entries == null) {
                return List.of();
            }

            List<WaypointSnapshot> waypoints = new ArrayList<>(entries.size());
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    continue;
                }
                WaypointSnapshot waypoint = parseWaypoint(element.getAsJsonObject());
                if (waypoint != null) {
                    waypoints.add(waypoint);
                }
            }
            return List.copyOf(waypoints);
        } catch (IOException | RuntimeException exception) {
            AllAdvancementsIgtTracker.LOGGER.warn("Failed to load tracker waypoints from {}", path, exception);
            return List.of();
        }
    }

    private void saveWaypoints(WorldKey worldKey, List<WaypointSnapshot> waypoints) {
        Path path = this.waypointsPath(worldKey);
        if (path == null) {
            return;
        }

        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonArray entries = new JsonArray();
            for (WaypointSnapshot waypoint : waypoints) {
                entries.add(serializeWaypoint(waypoint));
            }
            root.add("waypoints", entries);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException | RuntimeException exception) {
            AllAdvancementsIgtTracker.LOGGER.warn("Failed to save tracker waypoints to {}", path, exception);
        }
    }

    private Path waypointsPath(WorldKey worldKey) {
        if (worldKey == null || worldKey.value() == null || worldKey.value().isBlank()) {
            return null;
        }

        return Path.of(worldKey.value()).resolve("aaigttracker").resolve("waypoints.json");
    }

    private static WaypointSnapshot parseWaypoint(JsonObject object) {
        Identifier id = parseIdentifier(stringValue(object, "id"));
        Identifier dimensionId = parseIdentifier(stringValue(object, "dimension"));
        if (id == null || dimensionId == null) {
            return null;
        }

        return new WaypointSnapshot(
                id,
                normalizeLabel(stringValue(object, "label")),
                dimensionId,
                intValue(object, "x"),
                intValue(object, "y"),
                intValue(object, "z"),
                longValue(object, "created_at")
        );
    }

    private static JsonObject serializeWaypoint(WaypointSnapshot waypoint) {
        JsonObject object = new JsonObject();
        object.addProperty("id", waypoint.id().toString());
        object.addProperty("label", waypoint.label());
        object.addProperty("dimension", waypoint.dimensionId().toString());
        object.addProperty("x", waypoint.x());
        object.addProperty("y", waypoint.y());
        object.addProperty("z", waypoint.z());
        object.addProperty("created_at", waypoint.createdAtMillis());
        return object;
    }

    private static String normalizeLabel(String label) {
        if (label == null || label.isBlank()) {
            return "Waypoint";
        }

        String normalized = label.replaceAll("\\s+", " ").trim();
        return normalized.length() > 40 ? normalized.substring(0, 40).trim() : normalized;
    }

    private static String stringValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private static int intValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsInt() : 0;
    }

    private static long longValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsLong() : 0L;
    }

    private static Identifier parseIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Identifier.parse(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
