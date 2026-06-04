package dev.milow.aaigttracker.client.visual.capture;

import com.mojang.blaze3d.platform.NativeImage;
import dev.milow.aaigttracker.AllAdvancementsIgtTracker;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public final class IslandSoftwarePreviewRenderer implements AutoCloseable {
    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final int PARALLEL_FACE_THRESHOLD = 12_000;
    private static final int RENDER_SCALE = 2;

    private final Minecraft minecraft;
    private final Identifier textureId;
    private DynamicTexture texture;
    private NativeImage image;
    private float[] depthBuffer = new float[0];
    private int[] screenX = new int[0];
    private int[] screenY = new int[0];
    private float[] depth = new float[0];
    private int[] faceColor = new int[0];
    private int[] faceDetailColor = new int[0];
    private byte[] faceMaterial = new byte[0];
    private int width;
    private int height;

    public IslandSoftwarePreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.textureId = Identifier.fromNamespaceAndPath(
                AllAdvancementsIgtTracker.MOD_ID,
                "capture_preview/" + NEXT_ID.incrementAndGet()
        );
    }

    public void render(
            GuiGraphicsExtractor graphics,
            IslandPreviewMesh mesh,
            int x,
            int y,
            int width,
            int height,
            double yaw,
            double pitch,
            double zoom,
            double panX,
            double panY
    ) {
        this.renderToTexture(mesh, width, height, yaw, pitch, zoom, panX, panY);
        this.draw(graphics, x, y, width, height);
    }

    public void renderToTexture(
            IslandPreviewMesh mesh,
            int width,
            int height,
            double yaw,
            double pitch,
            double zoom,
            double panX,
            double panY
    ) {
        int renderWidth = Math.max(1, width * RENDER_SCALE);
        int renderHeight = Math.max(1, height * RENDER_SCALE);
        this.ensureTexture(renderWidth, renderHeight);
        this.rasterize(mesh, renderWidth, renderHeight, yaw, pitch, zoom * RENDER_SCALE, panX * RENDER_SCALE, panY * RENDER_SCALE);
        this.texture.upload();
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (this.texture == null || this.width <= 0 || this.height <= 0) {
            return;
        }

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                this.textureId,
                x,
                y,
                0.0F,
                0.0F,
                width,
                height,
                this.width,
                this.height,
                this.width,
                this.height
        );
    }

    @Override
    public void close() {
        if (this.texture != null) {
            this.minecraft.getTextureManager().release(this.textureId);
            this.texture = null;
            this.image = null;
        } else if (this.image != null) {
            this.image.close();
            this.image = null;
        }
    }

    private void ensureTexture(int width, int height) {
        if (this.texture != null && this.width == width && this.height == height) {
            return;
        }

        this.close();
        this.width = width;
        this.height = height;
        this.image = new NativeImage(width, height, false);
        this.texture = new DynamicTexture(() -> "AAIT biome island capture preview", this.image);
        this.minecraft.getTextureManager().register(this.textureId, this.texture);
        this.depthBuffer = new float[Math.max(1, width * height)];
    }

    private void rasterize(
            IslandPreviewMesh mesh,
            int width,
            int height,
            double yaw,
            double pitch,
            double zoom,
            double panX,
            double panY
    ) {
        this.image.fillRect(0, 0, width, height, 0);
        Arrays.fill(this.depthBuffer, Float.NEGATIVE_INFINITY);

        int faceCount = mesh.faceCount();
        this.ensureProjectionCapacity(faceCount);

        ProjectionContext context = new ProjectionContext(mesh, width, height, yaw, pitch, zoom, panX, panY);
        if (faceCount >= PARALLEL_FACE_THRESHOLD) {
            IntStream.range(0, faceCount).parallel().forEach(index -> this.projectFace(context, index));
        } else {
            for (int index = 0; index < faceCount; index++) {
                this.projectFace(context, index);
            }
        }

        for (int index = 0; index < faceCount; index++) {
            this.drawFaceQuad(context, index);
        }
    }

    private void ensureProjectionCapacity(int faceCount) {
        if (this.screenX.length >= faceCount) {
            return;
        }

        this.screenX = new int[faceCount];
        this.screenY = new int[faceCount];
        this.depth = new float[faceCount];
        this.faceColor = new int[faceCount];
        this.faceDetailColor = new int[faceCount];
        this.faceMaterial = new byte[faceCount];
    }

    private void projectFace(ProjectionContext context, int index) {
        IslandPreviewMesh mesh = context.mesh();
        Direction normal = mesh.normal(index);
        double nx = normal.getStepX();
        double ny = normal.getStepY();
        double nz = normal.getStepZ();
        double x = mesh.x(index) + 0.5 + nx * 0.52 - context.centerX();
        double y = mesh.y(index) + 0.5 + ny * 0.52 - context.centerY();
        double z = mesh.z(index) + 0.5 + nz * 0.52 - context.centerZ();

        double rx = x * context.cos() - z * context.sin();
        double rz = x * context.sin() + z * context.cos();
        double normalDepth = nx * context.sin() + nz * context.cos();
        this.screenX[index] = context.screenCenterX() + Mth.floor(rx * context.zoom());
        this.screenY[index] = context.screenCenterY() + Mth.floor((rz * context.pitch() - y * 0.86) * context.zoom());
        this.depth[index] = (float) (rz + y * 0.018 + normalDepth * 0.04);
        double shade = shadeFor(normal, normalDepth);
        this.faceColor[index] = shade(mesh.color(index), shade);
        this.faceDetailColor[index] = shade(mesh.detailColor(index), Math.max(0.35, shade * 0.88));
        this.faceMaterial[index] = mesh.material(index);
    }

    private void drawFaceQuad(ProjectionContext context, int index) {
        Direction normal = context.mesh().normal(index);
        AxisPair axes = axisPair(context, normal);
        double ax = axes.ax();
        double ay = axes.ay();
        double bx = axes.bx();
        double by = axes.by();
        double det = ax * by - ay * bx;
        if (Math.abs(det) < 0.0001) {
            return;
        }

        int centerX = this.screenX[index];
        int centerY = this.screenY[index];
        int minX = Math.max(0, Mth.floor(centerX - Math.abs(ax) * 0.55 - Math.abs(bx) * 0.55) - 1);
        int maxX = Math.min(this.width - 1, Mth.ceil(centerX + Math.abs(ax) * 0.55 + Math.abs(bx) * 0.55) + 1);
        int minY = Math.max(0, Mth.floor(centerY - Math.abs(ay) * 0.55 - Math.abs(by) * 0.55) - 1);
        int maxY = Math.min(this.height - 1, Mth.ceil(centerY + Math.abs(ay) * 0.55 + Math.abs(by) * 0.55) + 1);

        IslandPreviewMesh mesh = context.mesh();
        int blockX = mesh.x(index);
        int blockY = mesh.y(index);
        int blockZ = mesh.z(index);
        float faceDepth = this.depth[index];
        int baseColor = this.faceColor[index];
        int detailColor = this.faceDetailColor[index];
        byte material = this.faceMaterial[index];

        for (int py = minY; py <= maxY; py++) {
            int row = py * this.width;
            double relY = py + 0.5 - centerY;
            for (int px = minX; px <= maxX; px++) {
                double relX = px + 0.5 - centerX;
                double u = (relX * by - relY * bx) / det;
                double v = (ax * relY - ay * relX) / det;
                if (u < -0.5 || u > 0.5 || v < -0.5 || v > 0.5) {
                    continue;
                }

                int offset = row + px;
                if (faceDepth <= this.depthBuffer[offset]) {
                    continue;
                }

                int color = sampleTexture(material, baseColor, detailColor, normal, blockX, blockY, blockZ, u + 0.5, v + 0.5);
                this.depthBuffer[offset] = faceDepth;
                this.blendPixel(px, py, color);
            }
        }
    }

    private static AxisPair axisPair(ProjectionContext context, Direction normal) {
        return switch (normal) {
            case UP, DOWN -> new AxisPair(
                    projectX(context, 1.0, 0.0, 0.0),
                    projectY(context, 1.0, 0.0, 0.0),
                    projectX(context, 0.0, 0.0, 1.0),
                    projectY(context, 0.0, 0.0, 1.0)
            );
            case EAST, WEST -> new AxisPair(
                    projectX(context, 0.0, 1.0, 0.0),
                    projectY(context, 0.0, 1.0, 0.0),
                    projectX(context, 0.0, 0.0, 1.0),
                    projectY(context, 0.0, 0.0, 1.0)
            );
            case NORTH, SOUTH -> new AxisPair(
                    projectX(context, 1.0, 0.0, 0.0),
                    projectY(context, 1.0, 0.0, 0.0),
                    projectX(context, 0.0, 1.0, 0.0),
                    projectY(context, 0.0, 1.0, 0.0)
            );
        };
    }

    private static double projectX(ProjectionContext context, double x, double y, double z) {
        return (x * context.cos() - z * context.sin()) * context.zoom();
    }

    private static double projectY(ProjectionContext context, double x, double y, double z) {
        double rz = x * context.sin() + z * context.cos();
        return (rz * context.pitch() - y * 0.86) * context.zoom();
    }

    private void blendPixel(int x, int y, int color) {
        int alpha = (color >>> 24) & 255;
        if (alpha >= 250) {
            this.image.setPixelABGR(x, y, argbToAbgr(color));
            return;
        }

        int existing = abgrToArgb(this.image.getPixel(x, y));
        int inverse = 255 - alpha;
        int red = (((color >> 16) & 255) * alpha + ((existing >> 16) & 255) * inverse) / 255;
        int green = (((color >> 8) & 255) * alpha + ((existing >> 8) & 255) * inverse) / 255;
        int blue = ((color & 255) * alpha + (existing & 255) * inverse) / 255;
        this.image.setPixelABGR(x, y, argbToAbgr(0xFF000000 | red << 16 | green << 8 | blue));
    }

    private static int sampleTexture(byte material, int baseColor, int detailColor, Direction normal, int blockX, int blockY, int blockZ, double u, double v) {
        int noise = hash(blockX, blockY, blockZ, Mth.floor(u * 16.0), Mth.floor(v * 16.0));
        double grain = ((noise & 255) / 255.0) - 0.5;

        return switch (material) {
            case IslandPreviewMesh.PreviewMaterial.LEAVES -> sampleLeaves(baseColor, detailColor, noise, u, v);
            case IslandPreviewMesh.PreviewMaterial.BAMBOO -> sampleBamboo(baseColor, detailColor, noise, u, v, normal);
            case IslandPreviewMesh.PreviewMaterial.LOG -> sampleLog(baseColor, detailColor, noise, u, v, normal);
            case IslandPreviewMesh.PreviewMaterial.GRASS -> sampleGrass(baseColor, detailColor, noise, u, v, normal);
            case IslandPreviewMesh.PreviewMaterial.DIRT -> mix(baseColor, detailColor, Math.abs(grain) * 0.38);
            case IslandPreviewMesh.PreviewMaterial.STONE -> mix(baseColor, detailColor, Math.abs(grain) * 0.32);
            case IslandPreviewMesh.PreviewMaterial.SNOW -> mix(baseColor, detailColor, noise % 9 == 0 ? 0.28 : 0.04);
            case IslandPreviewMesh.PreviewMaterial.SAND -> mix(baseColor, detailColor, Math.abs(grain) * 0.24);
            case IslandPreviewMesh.PreviewMaterial.WATER -> sampleWater(baseColor, detailColor, noise, u, v);
            case IslandPreviewMesh.PreviewMaterial.LAVA -> sampleLava(baseColor, detailColor, noise, u, v);
            case IslandPreviewMesh.PreviewMaterial.PLANT -> samplePlant(baseColor, detailColor, noise, u, v);
            case IslandPreviewMesh.PreviewMaterial.NETHER -> mix(baseColor, detailColor, Math.abs(grain) * 0.45);
            case IslandPreviewMesh.PreviewMaterial.END -> mix(baseColor, detailColor, Math.abs(grain) * 0.20);
            case IslandPreviewMesh.PreviewMaterial.SCULK -> noise % 11 == 0 ? detailColor : mix(baseColor, detailColor, Math.abs(grain) * 0.20);
            case IslandPreviewMesh.PreviewMaterial.DRIPSTONE -> sampleDripstone(baseColor, detailColor, noise, u, v, normal);
            default -> mix(baseColor, detailColor, Math.abs(grain) * 0.18);
        };
    }

    private static int sampleLeaves(int baseColor, int detailColor, int noise, double u, double v) {
        if ((noise & 15) == 0) {
            return mix(baseColor, 0xFF111F10, 0.40);
        }
        if ((noise & 7) == 0 || stripe(u, 6.0, 0.055) || stripe(v, 6.0, 0.055)) {
            return mix(baseColor, detailColor, 0.32);
        }
        if ((noise & 31) == 3) {
            return mix(baseColor, 0xFFFFFFFF, 0.10);
        }
        return mix(baseColor, detailColor, ((noise >>> 8) & 7) / 64.0);
    }

    private static int sampleBamboo(int baseColor, int detailColor, int noise, double u, double v, Direction normal) {
        if (normal == Direction.UP || normal == Direction.DOWN) {
            return mix(baseColor, detailColor, Math.abs(u - 0.5) + Math.abs(v - 0.5) > 0.55 ? 0.45 : 0.08);
        }
        if (stripe(v, 4.0, 0.045)) {
            return mix(baseColor, 0xFFE0D45C, 0.42);
        }
        if (u < 0.18 || u > 0.82 || stripe(u, 3.0, 0.050)) {
            return mix(baseColor, detailColor, 0.50);
        }
        return mix(baseColor, detailColor, (noise & 7) / 80.0);
    }

    private static int sampleLog(int baseColor, int detailColor, int noise, double u, double v, Direction normal) {
        if (normal == Direction.UP || normal == Direction.DOWN) {
            double dx = u - 0.5;
            double dy = v - 0.5;
            double ring = Math.abs(Math.sin(Math.sqrt(dx * dx + dy * dy) * 42.0));
            return mix(baseColor, detailColor, ring > 0.72 ? 0.48 : 0.14);
        }
        if (stripe(u, 7.0, 0.060) || (noise & 15) == 2) {
            return mix(baseColor, detailColor, 0.42);
        }
        return mix(baseColor, detailColor, (noise & 7) / 90.0);
    }

    private static int sampleGrass(int baseColor, int detailColor, int noise, double u, double v, Direction normal) {
        if (normal != Direction.UP && normal != Direction.DOWN && v < 0.22) {
            return mix(detailColor, baseColor, 0.16);
        }
        if ((noise & 7) == 0 || stripe(u, 9.0, 0.035)) {
            return mix(baseColor, 0xFF233D18, 0.22);
        }
        return mix(baseColor, detailColor, (noise & 3) / 36.0);
    }

    private static int sampleWater(int baseColor, int detailColor, int noise, double u, double v) {
        int color = stripe(u + v, 5.5, 0.030) || (noise & 31) == 4 ? mix(baseColor, detailColor, 0.42) : baseColor;
        return color & 0xBFFFFFFF;
    }

    private static int sampleLava(int baseColor, int detailColor, int noise, double u, double v) {
        return (noise & 7) <= 2 || stripe(u - v, 4.0, 0.075) ? detailColor : mix(baseColor, 0xFF5A1400, 0.18);
    }

    private static int samplePlant(int baseColor, int detailColor, int noise, double u, double v) {
        if (u < 0.12 || u > 0.88 || stripe(u, 5.0, 0.050)) {
            return mix(baseColor, detailColor, 0.48);
        }
        return (noise & 5) == 0 ? mix(baseColor, 0xFFFFFFAA, 0.16) : baseColor;
    }

    private static int sampleDripstone(int baseColor, int detailColor, int noise, double u, double v, Direction normal) {
        if (normal != Direction.UP && normal != Direction.DOWN && (stripe(u, 5.0, 0.060) || (noise & 15) == 1)) {
            return mix(baseColor, detailColor, 0.45);
        }
        return mix(baseColor, detailColor, Math.abs(((noise & 255) / 255.0) - 0.5) * 0.30);
    }

    private static boolean stripe(double value, double frequency, double width) {
        double local = Math.abs(value * frequency - Math.floor(value * frequency + 0.5));
        return local < width;
    }

    private static int mix(int baseColor, int detailColor, double amount) {
        amount = Mth.clamp(amount, 0.0, 1.0);
        int alpha = (int) Mth.lerp(amount, (baseColor >>> 24) & 255, (detailColor >>> 24) & 255);
        int red = (int) Mth.lerp(amount, (baseColor >> 16) & 255, (detailColor >> 16) & 255);
        int green = (int) Mth.lerp(amount, (baseColor >> 8) & 255, (detailColor >> 8) & 255);
        int blue = (int) Mth.lerp(amount, baseColor & 255, detailColor & 255);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static double shadeFor(Direction normal, double normalDepth) {
        double shade = 0.68;
        if (normal == Direction.UP) {
            shade = 1.10;
        } else if (normal == Direction.DOWN) {
            shade = 0.48;
        }
        shade += Math.max(0.0, normalDepth) * 0.22;
        shade -= Math.max(0.0, -normalDepth) * 0.08;
        return Mth.clamp(shade, 0.42, 1.18);
    }

    private static int shade(int color, double factor) {
        int alpha = color & 0xFF000000;
        int red = Mth.clamp((int) (((color >> 16) & 255) * factor), 0, 255);
        int green = Mth.clamp((int) (((color >> 8) & 255) * factor), 0, 255);
        int blue = Mth.clamp((int) ((color & 255) * factor), 0, 255);
        return alpha | red << 16 | green << 8 | blue;
    }

    private static int argbToAbgr(int argb) {
        int alpha = argb & 0xFF000000;
        int red = (argb >> 16) & 255;
        int green = (argb >> 8) & 255;
        int blue = argb & 255;
        return alpha | blue << 16 | green << 8 | red;
    }

    private static int abgrToArgb(int abgr) {
        int alpha = abgr & 0xFF000000;
        int blue = (abgr >> 16) & 255;
        int green = (abgr >> 8) & 255;
        int red = abgr & 255;
        return alpha | red << 16 | green << 8 | blue;
    }

    private static int hash(int x, int y, int z, int u, int v) {
        int value = x * 73428767 ^ y * 912931 ^ z * 4382893 ^ u * 19349663 ^ v * 83492791;
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private record AxisPair(double ax, double ay, double bx, double by) {
    }

    private record ProjectionContext(
            IslandPreviewMesh mesh,
            int screenCenterX,
            int screenCenterY,
            double centerX,
            double centerY,
            double centerZ,
            double sin,
            double cos,
            double pitch,
            double zoom
    ) {
        ProjectionContext(
                IslandPreviewMesh mesh,
                int width,
                int height,
                double yaw,
                double pitch,
                double zoom,
                double panX,
                double panY
        ) {
            this(
                    mesh,
                    width / 2 + (int) panX,
                    height / 2 + (int) panY,
                    mesh.bounds().minX() + mesh.bounds().sizeX() / 2.0,
                    mesh.bounds().minY() + mesh.bounds().sizeY() / 2.0,
                    mesh.bounds().minZ() + mesh.bounds().sizeZ() / 2.0,
                    Math.sin(yaw),
                    Math.cos(yaw),
                    pitch,
                    zoom
            );
        }
    }
}
