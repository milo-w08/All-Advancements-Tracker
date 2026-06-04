package dev.milow.aaigttracker.client.visual.capture;

import dev.milow.aaigttracker.AllAdvancementsIgtTracker;
import dev.milow.aaigttracker.client.visual.BiomePreviewSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class IslandCapturePreviewCache implements AutoCloseable {
    public static final double DEFAULT_YAW = -0.75D;
    public static final double DEFAULT_PITCH = 0.46D;
    public static final double DEFAULT_ZOOM = 5.0D;
    public static final double MAX_ZOOM = 13.0D;

    private final Map<Identifier, IslandCaptureIndex.Entry> capturesByBiome = new HashMap<>();
    private final Map<Identifier, LoadedCapture> loadedCaptures = new HashMap<>();
    private final Map<ThumbnailKey, Thumbnail> thumbnails = new HashMap<>();
    private IslandSoftwarePreviewRenderer fallbackRenderer;
    private int activeResourceManagerId;
    private boolean indexed;

    public boolean render(
            GuiGraphicsExtractor graphics,
            BiomePreviewSpec previewSpec,
            int x,
            int y,
            int width,
            int height,
            boolean modal,
            double yaw,
            double pitch,
            double zoom,
            double panX,
            double panY
    ) {
        if (previewSpec == null || previewSpec.biomeId() == null || width <= 0 || height <= 0) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!this.prepare(minecraft)) {
            return false;
        }

        LoadedCapture capture = this.loadedCapture(minecraft.getResourceManager(), previewSpec.biomeId());
        if (capture == null || capture.mesh().faceCount() <= 0) {
            return false;
        }

        if (modal) {
            this.renderWithFallback(graphics, capture, x, y, width, height, yaw, pitch, zoom, panX, panY);
        } else {
            this.renderThumbnail(graphics, capture, previewSpec.biomeId(), x, y, width, height);
        }
        return true;
    }

    public void retainModalPreviews(List<BiomePreviewSpec> previewSpecs) {
        // Loading is intentionally lazy: thumbnails stay cached, and GPU renderers are created only for extended previews.
    }

    public void releaseModalTextures() {
        if (this.fallbackRenderer != null) {
            this.fallbackRenderer.close();
            this.fallbackRenderer = null;
        }
        for (LoadedCapture capture : this.loadedCaptures.values()) {
            capture.closeGpuRenderer();
        }
    }

    @Override
    public void close() {
        this.releaseModalTextures();
        for (LoadedCapture capture : this.loadedCaptures.values()) {
            capture.close();
        }
        for (Thumbnail thumbnail : this.thumbnails.values()) {
            thumbnail.close();
        }
        this.thumbnails.clear();
        this.loadedCaptures.clear();
        this.capturesByBiome.clear();
        this.indexed = false;
        this.activeResourceManagerId = 0;
    }

    private boolean prepare(Minecraft minecraft) {
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return false;
        }

        int resourceManagerId = System.identityHashCode(minecraft.getResourceManager());
        if (this.activeResourceManagerId != resourceManagerId) {
            this.close();
            this.activeResourceManagerId = resourceManagerId;
        }

        if (!this.indexed) {
            this.index(minecraft.getResourceManager());
        }
        return true;
    }

    private void index(ResourceManager resourceManager) {
        this.capturesByBiome.clear();
        IslandCaptureIndex index = IslandCaptureStorage.loadIndex(resourceManager);
        for (IslandCaptureIndex.Entry entry : index.captures) {
            Identifier biomeId = parseIdentifier(entry.biomeId);
            if (biomeId != null) {
                this.capturesByBiome.put(biomeId, entry);
            }
        }
        this.indexed = true;
    }

    private LoadedCapture loadedCapture(ResourceManager resourceManager, Identifier biomeId) {
        LoadedCapture cached = this.loadedCaptures.get(biomeId);
        if (cached != null) {
            return cached;
        }

        IslandCaptureIndex.Entry entry = this.capturesByBiome.get(biomeId);
        if (entry == null) {
            return null;
        }

        IslandBlockMap blockMap = IslandCaptureStorage.loadCapture(resourceManager, entry).orElse(null);
        if (blockMap == null) {
            return null;
        }

        try {
            LoadedCapture loaded = new LoadedCapture(blockMap, IslandPreviewMesh.fromBlockMap(blockMap));
            this.loadedCaptures.put(biomeId, loaded);
            return loaded;
        } catch (RuntimeException exception) {
            AllAdvancementsIgtTracker.LOGGER.warn("Failed to build biome island capture mesh for {}", biomeId, exception);
            return null;
        }
    }

    private void renderThumbnail(
            GuiGraphicsExtractor graphics,
            LoadedCapture capture,
            Identifier biomeId,
            int x,
            int y,
            int width,
            int height
    ) {
        ThumbnailKey key = new ThumbnailKey(biomeId, width, height);
        Thumbnail thumbnail = this.thumbnails.get(key);
        if (thumbnail == null) {
            thumbnail = new Thumbnail(Minecraft.getInstance());
            double rowZoom = this.zoomFor(capture.mesh(), width, height, false);
            thumbnail.renderer().renderToTexture(
                    capture.mesh(),
                    width,
                    height,
                    DEFAULT_YAW,
                    DEFAULT_PITCH,
                    rowZoom,
                    0.0D,
                    0.0D
            );
            this.thumbnails.put(key, thumbnail);
        }

        thumbnail.renderer().draw(graphics, x, y, width, height);
    }

    private void renderWithFallback(
            GuiGraphicsExtractor graphics,
            LoadedCapture capture,
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
        if (!capture.gpuFailed()) {
            try {
                capture.gpuRenderer().render(graphics, capture.blockMap(), x, y, width, height, yaw, pitch, zoom, panX, panY);
                return;
            } catch (RuntimeException exception) {
                capture.markGpuFailed();
                AllAdvancementsIgtTracker.LOGGER.warn("Falling back to software biome island preview for {}", capture.blockMap().biomeId(), exception);
            }
        }

        if (this.fallbackRenderer == null) {
            this.fallbackRenderer = new IslandSoftwarePreviewRenderer(Minecraft.getInstance());
        }
        this.fallbackRenderer.render(graphics, capture.mesh(), x, y, width, height, yaw, pitch, zoom, panX, panY);
    }

    private double zoomFor(IslandPreviewMesh mesh, int width, int height, boolean modal) {
        IslandBlockMap.Bounds bounds = mesh.bounds();
        double horizontalSpan = Math.max(1.0D, Math.max(bounds.sizeX(), bounds.sizeZ()));
        double verticalSpan = Math.max(1.0D, bounds.sizeY() * 0.86D + horizontalSpan * 0.34D);
        double padding = modal ? 1.10D : 1.18D;
        double byWidth = width / (horizontalSpan * padding);
        double byHeight = height / (verticalSpan * padding);
        double zoom = Math.min(byWidth, byHeight);
        return Math.max(modal ? 1.0D : 0.12D, zoom);
    }

    private static Identifier parseIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Identifier.parse(value);
        } catch (RuntimeException exception) {
            AllAdvancementsIgtTracker.LOGGER.warn("Ignoring invalid biome capture id '{}'", value);
            return null;
        }
    }

    private static final class LoadedCapture implements AutoCloseable {
        private final IslandBlockMap blockMap;
        private final IslandPreviewMesh mesh;
        private IslandGpuPreviewRenderer gpuRenderer;
        private boolean gpuFailed;

        private LoadedCapture(IslandBlockMap blockMap, IslandPreviewMesh mesh) {
            this.blockMap = blockMap;
            this.mesh = mesh;
        }

        private IslandBlockMap blockMap() {
            return this.blockMap;
        }

        private IslandPreviewMesh mesh() {
            return this.mesh;
        }

        private IslandGpuPreviewRenderer gpuRenderer() {
            if (this.gpuRenderer == null) {
                this.gpuRenderer = new IslandGpuPreviewRenderer(Minecraft.getInstance());
            }
            return this.gpuRenderer;
        }

        private boolean gpuFailed() {
            return this.gpuFailed;
        }

        private void markGpuFailed() {
            this.gpuFailed = true;
        }

        private void closeGpuRenderer() {
            if (this.gpuRenderer != null) {
                this.gpuRenderer.close();
                this.gpuRenderer = null;
            }
            this.gpuFailed = false;
        }

        @Override
        public void close() {
            this.closeGpuRenderer();
        }
    }

    private record ThumbnailKey(Identifier biomeId, int width, int height) {
    }

    private record Thumbnail(IslandSoftwarePreviewRenderer renderer) implements AutoCloseable {
        Thumbnail(Minecraft minecraft) {
            this(new IslandSoftwarePreviewRenderer(minecraft));
        }

        @Override
        public void close() {
            this.renderer.close();
        }
    }
}
