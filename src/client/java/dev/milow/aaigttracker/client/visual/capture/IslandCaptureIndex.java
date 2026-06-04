package dev.milow.aaigttracker.client.visual.capture;

import java.util.ArrayList;
import java.util.List;

public final class IslandCaptureIndex {
    public int formatVersion = IslandCaptureStorage.FORMAT_VERSION;
    public String minecraftVersion = "";
    public int radius = IslandCaptureStorage.CAPTURE_RADIUS;
    public List<Entry> captures = new ArrayList<>();

    public static final class Entry {
        public String biomeId = "";
        public String fileName = "";
        public int radius;
        public int paletteCount;
        public int blockCount;
        public int entityCount;
        public long capturedAt;
    }
}
