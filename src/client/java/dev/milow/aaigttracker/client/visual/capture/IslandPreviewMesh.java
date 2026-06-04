package dev.milow.aaigttracker.client.visual.capture;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

public final class IslandPreviewMesh {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final IslandBlockMap.Bounds bounds;
    private final short[] x;
    private final short[] y;
    private final short[] z;
    private final byte[] normal;
    private final int[] color;
    private final int[] detailColor;
    private final byte[] material;

    private IslandPreviewMesh(
            IslandBlockMap.Bounds bounds,
            short[] x,
            short[] y,
            short[] z,
            byte[] normal,
            int[] color,
            int[] detailColor,
            byte[] material
    ) {
        this.bounds = bounds;
        this.x = x;
        this.y = y;
        this.z = z;
        this.normal = normal;
        this.color = color;
        this.detailColor = detailColor;
        this.material = material;
    }

    public static IslandPreviewMesh fromBlockMap(IslandBlockMap blockMap) {
        Map<Long, Integer> paletteByCoordinate = new HashMap<>(blockMap.blocks().size() * 2);
        for (IslandBlockMap.BlockEntry block : blockMap.blocks()) {
            paletteByCoordinate.put(key(block.x(), block.y(), block.z()), block.paletteIndex());
        }

        int capacity = Math.max(1, blockMap.blocks().size() * DIRECTIONS.length);
        short[] x = new short[capacity];
        short[] y = new short[capacity];
        short[] z = new short[capacity];
        byte[] normal = new byte[capacity];
        int[] color = new int[capacity];
        int[] detailColor = new int[capacity];
        byte[] material = new byte[capacity];
        int faceCount = 0;

        PreviewMaterial[] materials = blockMap.palette().stream()
                .map(PreviewMaterial::fromPalette)
                .toArray(PreviewMaterial[]::new);

        for (IslandBlockMap.BlockEntry block : blockMap.blocks()) {
            if (block.paletteIndex() < 0 || block.paletteIndex() >= blockMap.palette().size()) {
                continue;
            }

            IslandBlockMap.PaletteEntry current = blockMap.palette().get(block.paletteIndex());
            PreviewMaterial visual = materials[block.paletteIndex()];
            for (int directionIndex = 0; directionIndex < DIRECTIONS.length; directionIndex++) {
                Direction direction = DIRECTIONS[directionIndex];
                Integer neighborPaletteIndex = paletteByCoordinate.get(key(
                        block.x() + direction.getStepX(),
                        block.y() + direction.getStepY(),
                        block.z() + direction.getStepZ()
                ));
                if (!isVisibleFace(current, neighborPaletteIndex, blockMap)) {
                    continue;
                }

                x[faceCount] = (short) block.x();
                y[faceCount] = (short) block.y();
                z[faceCount] = (short) block.z();
                normal[faceCount] = (byte) directionIndex;
                color[faceCount] = visual.baseColor();
                detailColor[faceCount] = visual.detailColor();
                material[faceCount] = visual.kind();
                faceCount++;
            }
        }

        return new IslandPreviewMesh(
                blockMap.bounds(),
                Arrays.copyOf(x, faceCount),
                Arrays.copyOf(y, faceCount),
                Arrays.copyOf(z, faceCount),
                Arrays.copyOf(normal, faceCount),
                Arrays.copyOf(color, faceCount),
                Arrays.copyOf(detailColor, faceCount),
                Arrays.copyOf(material, faceCount)
        );
    }

    public IslandBlockMap.Bounds bounds() {
        return this.bounds;
    }

    public int faceCount() {
        return this.color.length;
    }

    public int x(int index) {
        return this.x[index];
    }

    public int y(int index) {
        return this.y[index];
    }

    public int z(int index) {
        return this.z[index];
    }

    public Direction normal(int index) {
        return DIRECTIONS[this.normal[index] & 255];
    }

    public int color(int index) {
        return this.color[index];
    }

    public int detailColor(int index) {
        return this.detailColor[index];
    }

    public byte material(int index) {
        return this.material[index];
    }

    private static boolean isVisibleFace(IslandBlockMap.PaletteEntry current, Integer neighborPaletteIndex, IslandBlockMap blockMap) {
        if (neighborPaletteIndex == null || neighborPaletteIndex < 0 || neighborPaletteIndex >= blockMap.palette().size()) {
            return true;
        }

        IslandBlockMap.PaletteEntry neighbor = blockMap.palette().get(neighborPaletteIndex);
        if (current.fluid() != neighbor.fluid()) {
            return true;
        }
        return !neighbor.occluding();
    }

    private static long key(int x, int y, int z) {
        return (((long) x & 0x1FFFFFL) << 42) | (((long) y & 0x1FFFFFL) << 21) | ((long) z & 0x1FFFFFL);
    }

    public static final class PreviewMaterial {
        public static final byte DEFAULT = 0;
        public static final byte GRASS = 1;
        public static final byte DIRT = 2;
        public static final byte STONE = 3;
        public static final byte SNOW = 4;
        public static final byte SAND = 5;
        public static final byte WATER = 6;
        public static final byte LAVA = 7;
        public static final byte LEAVES = 8;
        public static final byte LOG = 9;
        public static final byte BAMBOO = 10;
        public static final byte PLANT = 11;
        public static final byte NETHER = 12;
        public static final byte END = 13;
        public static final byte SCULK = 14;
        public static final byte DRIPSTONE = 15;

        private final byte kind;
        private final int baseColor;
        private final int detailColor;

        private PreviewMaterial(byte kind, int baseColor, int detailColor) {
            this.kind = kind;
            this.baseColor = baseColor;
            this.detailColor = detailColor;
        }

        public static PreviewMaterial fromPalette(IslandBlockMap.PaletteEntry entry) {
            String state = entry.state().toLowerCase(Locale.ROOT);
            if (state.contains("bamboo")) {
                return new PreviewMaterial(BAMBOO, 0xFF8DBE35, 0xFF2C5A16);
            }
            if (state.contains("cherry") && (state.contains("leaves") || state.contains("petals"))) {
                return new PreviewMaterial(LEAVES, 0xFFFF9FCB, 0xFFC85C94);
            }
            if (state.contains("leaves")) {
                return new PreviewMaterial(LEAVES, chooseLeafColor(state, entry.color()), 0xFF1E4D1E);
            }
            if (state.contains("log") || state.contains("stem") || state.contains("hyphae")) {
                return new PreviewMaterial(LOG, chooseWoodColor(state, entry.color()), 0xFF4A2A14);
            }
            if (state.contains("grass_block") || state.contains("podzol") || state.contains("mycelium")) {
                return new PreviewMaterial(GRASS, chooseGrassColor(state, entry.color()), 0xFF6A4328);
            }
            if (state.contains("dirt") || state.contains("mud") || state.contains("farmland")) {
                return new PreviewMaterial(DIRT, normalize(entry.color(), 0xFF7A5435), 0xFF4A3323);
            }
            if (state.contains("snow") || state.contains("ice")) {
                return new PreviewMaterial(SNOW, 0xFFE8F4FF, 0xFFAFC8E2);
            }
            if (state.contains("sand")) {
                boolean red = state.contains("red_sand");
                return new PreviewMaterial(SAND, red ? 0xFFD88445 : 0xFFE3D39A, red ? 0xFF9E542B : 0xFFB7A467);
            }
            if (state.contains("water")) {
                return new PreviewMaterial(WATER, 0xAA2D65D9, 0xAA75B9FF);
            }
            if (state.contains("lava") || state.contains("magma")) {
                return new PreviewMaterial(LAVA, 0xFFFF7A10, 0xFFFFD95A);
            }
            if (state.contains("sculk")) {
                return new PreviewMaterial(SCULK, 0xFF082229, 0xFF16A3A3);
            }
            if (state.contains("dripstone")) {
                return new PreviewMaterial(DRIPSTONE, 0xFF8F6A5B, 0xFF4D3834);
            }
            if (state.contains("netherrack")
                    || state.contains("nylium")
                    || state.contains("basalt")
                    || state.contains("blackstone")
                    || state.contains("soul_")) {
                return new PreviewMaterial(NETHER, normalize(entry.color(), 0xFF7B302C), 0xFF251418);
            }
            if (state.contains("end_stone") || state.contains("chorus")) {
                return new PreviewMaterial(END, normalize(entry.color(), 0xFFE0D890), 0xFF9E995F);
            }
            if (state.contains("stone")
                    || state.contains("deepslate")
                    || state.contains("ore")
                    || state.contains("tuff")
                    || state.contains("granite")
                    || state.contains("diorite")
                    || state.contains("andesite")) {
                return new PreviewMaterial(STONE, normalize(entry.color(), 0xFF777777), 0xFF414141);
            }
            if (state.contains("grass")
                    || state.contains("fern")
                    || state.contains("flower")
                    || state.contains("vine")
                    || state.contains("kelp")
                    || state.contains("seagrass")
                    || state.contains("sapling")
                    || state.contains("azalea")
                    || state.contains("fungus")
                    || state.contains("roots")) {
                return new PreviewMaterial(PLANT, normalize(entry.color(), 0xFF4E9B35), 0xFF214E19);
            }
            return new PreviewMaterial(DEFAULT, normalize(entry.color(), 0xFF777777), darken(normalize(entry.color(), 0xFF777777), 0.58));
        }

        public byte kind() {
            return this.kind;
        }

        public int baseColor() {
            return this.baseColor;
        }

        public int detailColor() {
            return this.detailColor;
        }

        private static int chooseLeafColor(String state, int fallback) {
            if (state.contains("spruce")) {
                return 0xFF255C3A;
            }
            if (state.contains("dark_oak")) {
                return 0xFF244D20;
            }
            if (state.contains("jungle")) {
                return 0xFF2F7B2E;
            }
            if (state.contains("mangrove")) {
                return 0xFF466D2E;
            }
            return normalize(fallback, 0xFF3F8B30);
        }

        private static int chooseGrassColor(String state, int fallback) {
            if (state.contains("podzol")) {
                return 0xFF6C5C34;
            }
            if (state.contains("mycelium")) {
                return 0xFF7C6D84;
            }
            return normalize(fallback, 0xFF5EA83D);
        }

        private static int chooseWoodColor(String state, int fallback) {
            if (state.contains("birch")) {
                return 0xFFE0D2A8;
            }
            if (state.contains("spruce") || state.contains("dark_oak")) {
                return 0xFF5A351B;
            }
            if (state.contains("crimson")) {
                return 0xFF7D244E;
            }
            if (state.contains("warped")) {
                return 0xFF2A7C78;
            }
            return normalize(fallback, 0xFF87552D);
        }

        private static int normalize(int color, int fallback) {
            int alpha = color & 0xFF000000;
            return alpha == 0 ? fallback : color | 0xFF000000;
        }

        private static int darken(int color, double factor) {
            int red = Mth.clamp((int) (((color >> 16) & 255) * factor), 0, 255);
            int green = Mth.clamp((int) (((color >> 8) & 255) * factor), 0, 255);
            int blue = Mth.clamp((int) ((color & 255) * factor), 0, 255);
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }
    }
}
