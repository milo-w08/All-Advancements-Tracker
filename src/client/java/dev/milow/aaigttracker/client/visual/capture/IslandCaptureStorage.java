package dev.milow.aaigttracker.client.visual.capture;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class IslandCaptureStorage {
    public static final int FORMAT_VERSION = 3;
    public static final int CAPTURE_RADIUS = 32;

    private static final String NAMESPACE = "aaigttracker";
    private static final String CAPTURE_ROOT = "captures";
    private static final String INDEX_FILE = "captures.index.json";
    private static final byte[] MAGIC = new byte[] {'S', 'B', 'I', 'C'};
    private static final Gson GSON = new GsonBuilder().create();

    private IslandCaptureStorage() {
    }

    public static IslandCaptureIndex loadIndex(ResourceManager resourceManager) {
        if (resourceManager == null) {
            return newIndex();
        }

        Identifier indexId = Identifier.fromNamespaceAndPath(NAMESPACE, CAPTURE_ROOT + "/" + INDEX_FILE);
        Resource resource = resourceManager.getResource(indexId).orElse(null);
        if (resource == null) {
            return newIndex();
        }

        try (Reader reader = resource.openAsReader()) {
            IslandCaptureIndex index = GSON.fromJson(reader, IslandCaptureIndex.class);
            return index == null ? newIndex() : normalize(index);
        } catch (IOException | RuntimeException exception) {
            return newIndex();
        }
    }

    public static Optional<IslandBlockMap> loadCapture(ResourceManager resourceManager, IslandCaptureIndex.Entry entry) {
        if (resourceManager == null || entry == null || entry.fileName == null || entry.fileName.isBlank()) {
            return Optional.empty();
        }

        Identifier captureId = Identifier.fromNamespaceAndPath(NAMESPACE, CAPTURE_ROOT + "/" + entry.fileName);
        Resource resource = resourceManager.getResource(captureId).orElse(null);
        if (resource == null) {
            return Optional.empty();
        }

        try (InputStream input = resource.open()) {
            return Optional.of(readBlockMap(input));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static IslandBlockMap readBlockMap(InputStream input) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(input)))) {
            byte[] magic = in.readNBytes(MAGIC.length);
            for (int index = 0; index < MAGIC.length; index++) {
                if (index >= magic.length || magic[index] != MAGIC[index]) {
                    throw new IOException("Invalid SingleBiomeIsland capture");
                }
            }

            int version = in.readUnsignedByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported SingleBiomeIsland capture version: " + version);
            }

            String biomeId = in.readUTF();
            int radius = readVarInt(in);
            IslandBlockMap.Bounds bounds = new IslandBlockMap.Bounds(
                    readSignedVarInt(in),
                    readSignedVarInt(in),
                    readSignedVarInt(in),
                    readVarInt(in),
                    readVarInt(in),
                    readVarInt(in)
            );

            int paletteCount = readVarInt(in);
            List<IslandBlockMap.PaletteEntry> palette = new ArrayList<>(paletteCount);
            for (int index = 0; index < paletteCount; index++) {
                String state = in.readUTF();
                int color = in.readInt();
                int flags = version >= 2 ? readVarInt(in) : inferFlags(state);
                palette.add(new IslandBlockMap.PaletteEntry(state, color, flags));
            }

            int blockCount = readVarInt(in);
            List<IslandBlockMap.BlockEntry> blocks = new ArrayList<>(blockCount);
            long currentKey = 0L;
            for (int index = 0; index < blockCount; index++) {
                currentKey += readVarLong(in);
                int paletteIndex = readVarInt(in);
                blocks.add(blockFromKey(bounds, currentKey, paletteIndex));
            }

            List<IslandBlockMap.EntityPaletteEntry> entityPalette = List.of();
            List<IslandBlockMap.EntityEntry> entities = List.of();
            if (version >= 3) {
                int entityPaletteCount = readVarInt(in);
                List<IslandBlockMap.EntityPaletteEntry> mutableEntityPalette = new ArrayList<>(entityPaletteCount);
                for (int index = 0; index < entityPaletteCount; index++) {
                    mutableEntityPalette.add(new IslandBlockMap.EntityPaletteEntry(in.readUTF()));
                }

                int entityCount = readVarInt(in);
                List<IslandBlockMap.EntityEntry> mutableEntities = new ArrayList<>(entityCount);
                for (int index = 0; index < entityCount; index++) {
                    mutableEntities.add(new IslandBlockMap.EntityEntry(
                            readVarInt(in),
                            readSignedVarInt(in),
                            readSignedVarInt(in),
                            readSignedVarInt(in),
                            in.readUnsignedByte(),
                            in.readUnsignedByte(),
                            in.readUnsignedByte(),
                            readVarInt(in),
                            in.readUTF()
                    ));
                }
                entityPalette = List.copyOf(mutableEntityPalette);
                entities = List.copyOf(mutableEntities);
            }

            return new IslandBlockMap(biomeId, radius, bounds, List.copyOf(palette), List.copyOf(blocks), entityPalette, entities);
        }
    }

    private static IslandCaptureIndex newIndex() {
        return new IslandCaptureIndex();
    }

    private static IslandCaptureIndex normalize(IslandCaptureIndex index) {
        index.formatVersion = FORMAT_VERSION;
        if (index.captures == null) {
            index.captures = new ArrayList<>();
        }
        index.captures.removeIf(entry -> entry == null || entry.biomeId == null || entry.fileName == null);
        index.captures.sort(Comparator.comparing(entry -> entry.biomeId));
        return index;
    }

    private static int inferFlags(String state) {
        String normalized = state.toLowerCase();
        int flags = 0;
        if (normalized.contains("water") || normalized.contains("lava") || normalized.contains("kelp") || normalized.contains("seagrass")) {
            flags |= IslandBlockMap.PaletteEntry.FLUID;
        }
        if (!normalized.contains("leaves")
                && !normalized.contains("grass")
                && !normalized.contains("flower")
                && !normalized.contains("sapling")
                && !normalized.contains("vine")
                && !normalized.contains("kelp")
                && !normalized.contains("seagrass")
                && !normalized.contains("coral")
                && !normalized.contains("dripstone")
                && !normalized.contains("sculk_sensor")
                && !normalized.contains("sculk_shrieker")
                && !normalized.contains("water")
                && !normalized.contains("lava")) {
            flags |= IslandBlockMap.PaletteEntry.OCCLUDING;
        }
        return flags;
    }

    private static IslandBlockMap.BlockEntry blockFromKey(IslandBlockMap.Bounds bounds, long key, int paletteIndex) {
        int z = (int) (key % bounds.sizeZ());
        long withoutZ = key / bounds.sizeZ();
        int y = (int) (withoutZ % bounds.sizeY());
        int x = (int) (withoutZ / bounds.sizeY());
        return new IslandBlockMap.BlockEntry(bounds.minX() + x, bounds.minY() + y, bounds.minZ() + z, paletteIndex);
    }

    private static int readSignedVarInt(DataInputStream in) throws IOException {
        int value = readVarInt(in);
        return (value >>> 1) ^ -(value & 1);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (shift < 35) {
            int current = in.read();
            if (current < 0) {
                throw new EOFException();
            }
            value |= (current & 127) << shift;
            if ((current & 128) == 0) {
                return value;
            }
            shift += 7;
        }
        throw new IOException("VarInt too long");
    }

    private static long readVarLong(DataInputStream in) throws IOException {
        long value = 0L;
        int shift = 0;
        while (shift < 70) {
            int current = in.read();
            if (current < 0) {
                throw new EOFException();
            }
            value |= (long) (current & 127) << shift;
            if ((current & 128) == 0) {
                return value;
            }
            shift += 7;
        }
        throw new IOException("VarLong too long");
    }
}
