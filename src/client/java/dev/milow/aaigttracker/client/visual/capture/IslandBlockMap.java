package dev.milow.aaigttracker.client.visual.capture;

import java.util.List;

public record IslandBlockMap(
        String biomeId,
        int radius,
        Bounds bounds,
        List<PaletteEntry> palette,
        List<BlockEntry> blocks,
        List<EntityPaletteEntry> entityPalette,
        List<EntityEntry> entities
) {
    public record Bounds(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ) {
    }

    public record PaletteEntry(String state, int color, int flags) {
        public static final int OCCLUDING = 1;
        public static final int FLUID = 1 << 1;

        public boolean occluding() {
            return (this.flags & OCCLUDING) != 0;
        }

        public boolean fluid() {
            return (this.flags & FLUID) != 0;
        }
    }

    public record BlockEntry(int x, int y, int z, int paletteIndex) {
    }

    public record EntityPaletteEntry(String typeId) {
    }

    public record EntityEntry(int typeIndex, int x16, int y16, int z16, int yaw, int pitch, int headYaw, int flags, String visualData) {
        public static final int BABY = 1;
    }
}
