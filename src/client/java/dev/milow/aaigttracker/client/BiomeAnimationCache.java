package dev.milow.aaigttracker.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public final class BiomeAnimationCache implements AutoCloseable {
    private static final String NAMESPACE = "aaigttracker";
    private static final String BIOME_ROOT = "biomes";
    private static final String BIOME_PREVIEW_ROOT = "biome_previews";
    private static final String TEXTURE_PREFIX = "biome_animation";
    private static final int MODERN_FRAME_WIDTH = 864;
    private static final int MODERN_FRAME_HEIGHT = 540;
    private static final int LEGACY_FRAME_WIDTH = 432;
    private static final int LEGACY_FRAME_HEIGHT = 270;

    private final Map<String, List<AnimationMetadata>> metadataBySlug = new HashMap<>();
    private final Map<Identifier, CachedFrame> previewFrames = new HashMap<>();
    private final Map<Identifier, CachedSheet> sheets = new HashMap<>();
    private final Map<Identifier, PendingSheetLoad> pendingSheets = new HashMap<>();
    private final ScheduledThreadPoolExecutor decodeExecutor = createDecodeExecutor();
    private boolean indexed;
    private int activeResourceManagerId;

    public CachedFrame getPreviewFrame(BiomePreviewSpec previewSpec) {
        if (previewSpec == null || previewSpec.biomeId() == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!this.prepare(minecraft)) {
            return null;
        }

        Identifier biomeId = previewSpec.biomeId();
        CachedFrame cachedFrame = this.previewFrames.get(biomeId);
        if (cachedFrame != null) {
            return cachedFrame;
        }

        Identifier previewId = previewImageId(biomeId);
        NativeImage preview = readImage(minecraft, previewId);
        if (preview == null) {
            return null;
        }

        try {
            int previewWidth = preview.getWidth();
            int previewHeight = preview.getHeight();
            Identifier textureId = textureId(biomeId, "preview");
            DynamicTexture texture = new DynamicTexture(
                    () -> "AAIT biome preview " + biomeId,
                    preview
            );
            preview = null;
            minecraft.getTextureManager().register(textureId, texture);
            texture.upload();

            CachedFrame cached = new CachedFrame(textureId, previewWidth, previewHeight);
            this.previewFrames.put(biomeId, cached);
            return cached;
        } finally {
            if (preview != null) {
                preview.close();
            }
        }
    }

    public CachedSheet getSheet(BiomePreviewSpec previewSpec) {
        if (previewSpec == null || previewSpec.biomeId() == null) {
            return null;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!this.prepare(minecraft)) {
            return null;
        }

        Identifier biomeId = previewSpec.biomeId();
        CachedSheet cachedSheet = this.sheets.get(biomeId);
        if (cachedSheet != null) {
            return cachedSheet;
        }

        this.requestSheet(previewSpec);
        PendingSheetLoad pendingSheet = this.pendingSheets.get(biomeId);
        if (pendingSheet == null) {
            return null;
        }

        CachedSheet resolvedSheet = this.resolvePendingSheet(minecraft, biomeId, pendingSheet);
        if (resolvedSheet != null) {
            return resolvedSheet;
        }

        if (pendingSheet.isFinished()) {
            this.pendingSheets.remove(biomeId);
        }
        return null;
    }

    public void requestSheet(BiomePreviewSpec previewSpec) {
        if (previewSpec == null || previewSpec.biomeId() == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!this.prepare(minecraft)) {
            return;
        }

        Identifier biomeId = previewSpec.biomeId();
        if (this.sheets.containsKey(biomeId) || this.pendingSheets.containsKey(biomeId)) {
            return;
        }

        AnimationMetadata metadata = this.metadataFor(biomeId);
        if (metadata == null) {
            return;
        }

        PendingSheetLoad pendingSheet = new PendingSheetLoad(metadata);
        this.pendingSheets.put(biomeId, pendingSheet);
        Future<?> task = this.decodeExecutor.submit(() -> {
            NativeImage sheet = null;
            try {
                sheet = readImage(minecraft, metadata.sheetId());
            } finally {
                pendingSheet.complete(sheet);
            }
        });
        pendingSheet.attach(task);
    }

    public void retainSheets(List<BiomePreviewSpec> previewSpecs) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!this.prepare(minecraft)) {
            return;
        }

        Set<Identifier> retainedBiomeIds = new HashSet<>();
        if (previewSpecs != null) {
            for (BiomePreviewSpec previewSpec : previewSpecs) {
                if (previewSpec != null && previewSpec.biomeId() != null) {
                    retainedBiomeIds.add(previewSpec.biomeId());
                    this.requestSheet(previewSpec);
                }
            }
        }

        for (Identifier biomeId : new ArrayList<>(this.pendingSheets.keySet())) {
            if (!retainedBiomeIds.contains(biomeId)) {
                this.cancelPendingSheet(biomeId);
            }
        }

        for (Identifier biomeId : new ArrayList<>(this.sheets.keySet())) {
            if (!retainedBiomeIds.contains(biomeId)) {
                this.releaseSheet(minecraft, biomeId);
            }
        }
    }

    public void releaseSheets() {
        Minecraft minecraft = Minecraft.getInstance();
        this.releaseSheets(minecraft);
    }

    @Override
    public void close() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            this.releaseRegisteredTextures(minecraft);
        } else {
            this.releasePendingSheets();
            this.previewFrames.clear();
            this.sheets.clear();
        }
        this.decodeExecutor.shutdownNow();
    }

    private boolean prepare(Minecraft minecraft) {
        if (minecraft == null || minecraft.getResourceManager() == null) {
            return false;
        }

        int resourceManagerId = System.identityHashCode(minecraft.getResourceManager());
        if (this.activeResourceManagerId != resourceManagerId) {
            this.releaseRegisteredTextures(minecraft);
            this.metadataBySlug.clear();
            this.indexed = false;
            this.activeResourceManagerId = resourceManagerId;
        }

        if (!this.indexed) {
            this.indexResources(minecraft);
            this.indexed = true;
        }
        return true;
    }

    private void indexResources(Minecraft minecraft) {
        Map<Identifier, Resource> resources = minecraft.getResourceManager().listResources(
                BIOME_ROOT,
                id -> NAMESPACE.equals(id.getNamespace()) && id.getPath().endsWith(".json")
        );

        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            AnimationMetadata metadata = this.readMetadata(entry.getKey(), entry.getValue());
            if (metadata == null) {
                continue;
            }

            this.metadataBySlug
                    .computeIfAbsent(metadata.slug(), ignored -> new ArrayList<>())
                    .add(metadata);
        }

        for (List<AnimationMetadata> metadata : this.metadataBySlug.values()) {
            metadata.sort(Comparator
                    .comparingInt(BiomeAnimationCache::preferenceScore)
                    .thenComparingInt(value -> value.frameWidth() * value.frameHeight())
                    .thenComparing(value -> value.jsonId().toString()));
        }
    }

    private AnimationMetadata readMetadata(Identifier jsonId, Resource resource) {
        try (var reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            int frames = jsonInt(json, "frames");
            int columns = jsonInt(json, "columns");
            int rows = jsonInt(json, "rows");
            int frameWidth = jsonInt(json, "frame_width");
            int frameHeight = jsonInt(json, "frame_height");
            int sheetWidth = jsonInt(json, "sheet_width");
            int sheetHeight = jsonInt(json, "sheet_height");
            double fps = jsonDouble(json, "fps");
            String file = jsonString(json, "file");

            if (frames <= 0 || columns <= 0 || rows <= 0 || fps <= 0.0D || file.isBlank()) {
                return null;
            }
            if (!this.isSupportedFrameSize(frameWidth, frameHeight)) {
                return null;
            }
            if (frames > columns * rows) {
                return null;
            }

            Identifier sheetId = sibling(jsonId, file);
            String slug = slugFrom(jsonId, frameWidth, frameHeight, fps);
            return new AnimationMetadata(
                    slug,
                    jsonId,
                    sheetId,
                    frames,
                    columns,
                    rows,
                    frameWidth,
                    frameHeight,
                    sheetWidth,
                    sheetHeight,
                    fps
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private AnimationMetadata metadataFor(Identifier biomeId) {
        List<AnimationMetadata> candidates = this.metadataBySlug.get(biomeSlug(biomeId));
        return candidates == null || candidates.isEmpty() ? null : candidates.get(0);
    }

    private CachedSheet resolvePendingSheet(Minecraft minecraft, Identifier biomeId, PendingSheetLoad pendingSheet) {
        if (pendingSheet == null || !pendingSheet.isFinished()) {
            return null;
        }

        NativeImage sheet = pendingSheet.takeImage();
        this.pendingSheets.remove(biomeId, pendingSheet);
        if (sheet == null) {
            return null;
        }

        AnimationMetadata metadata = pendingSheet.metadata();
        if (!metadata.fits(sheet.getWidth(), sheet.getHeight())) {
            closeImage(sheet);
            return null;
        }

        Identifier textureId = textureId(biomeId, "sheet");
        DynamicTexture texture = new DynamicTexture(
                () -> "AAIT biome animation sheet " + biomeId,
                sheet
        );
        minecraft.getTextureManager().register(textureId, texture);
        texture.upload();

        CachedSheet cachedSheet = new CachedSheet(
                textureId,
                sheet.getWidth(),
                sheet.getHeight(),
                metadata.frames(),
                metadata.columns(),
                metadata.frameWidth(),
                metadata.frameHeight(),
                metadata.fps()
        );
        this.sheets.put(biomeId, cachedSheet);
        return cachedSheet;
    }

    private void releaseSheets(Minecraft minecraft) {
        this.releasePendingSheets();

        if (minecraft == null) {
            this.sheets.clear();
            return;
        }

        for (Identifier biomeId : new ArrayList<>(this.sheets.keySet())) {
            this.releaseSheet(minecraft, biomeId);
        }
    }

    private void releasePendingSheets() {
        for (Identifier biomeId : new ArrayList<>(this.pendingSheets.keySet())) {
            this.cancelPendingSheet(biomeId);
        }
    }

    private void cancelPendingSheet(Identifier biomeId) {
        PendingSheetLoad pendingSheet = this.pendingSheets.remove(biomeId);
        if (pendingSheet != null) {
            pendingSheet.cancel();
        }
    }

    private void releaseRegisteredTextures(Minecraft minecraft) {
        for (CachedFrame preview : this.previewFrames.values()) {
            minecraft.getTextureManager().release(preview.textureId());
        }
        this.previewFrames.clear();
        this.releaseSheets(minecraft);
    }

    private void releaseSheet(Minecraft minecraft, Identifier biomeId) {
        CachedSheet sheet = this.sheets.remove(biomeId);
        if (sheet != null) {
            minecraft.getTextureManager().release(sheet.textureId());
        }
    }

    private static ScheduledThreadPoolExecutor createDecodeExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
                1,
                BiomeAnimationCache::newDecodeThread
        );
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private static Thread newDecodeThread(Runnable runnable) {
        Thread thread = new Thread(runnable, "AAIT biome animation decode");
        thread.setDaemon(true);
        return thread;
    }

    private static void closeImage(NativeImage image) {
        if (image == null) {
            return;
        }

        try {
            image.close();
        } catch (Exception ignored) {
        }
    }

    private static final class PendingSheetLoad {
        private final AnimationMetadata metadata;
        private Future<?> task;
        private NativeImage image;
        private boolean finished;
        private boolean cancelled;

        private PendingSheetLoad(AnimationMetadata metadata) {
            this.metadata = metadata;
        }

        private AnimationMetadata metadata() {
            return this.metadata;
        }

        private synchronized void attach(Future<?> task) {
            this.task = task;
        }

        private synchronized void complete(NativeImage image) {
            this.finished = true;
            if (this.cancelled) {
                closeImage(image);
                return;
            }

            this.image = image;
        }

        private synchronized boolean isFinished() {
            return this.finished;
        }

        private synchronized NativeImage takeImage() {
            if (!this.finished) {
                return null;
            }

            NativeImage image = this.image;
            this.image = null;
            return image;
        }

        private synchronized void cancel() {
            this.cancelled = true;
            this.finished = true;
            if (this.task != null) {
                this.task.cancel(true);
            }
            closeImage(this.image);
            this.image = null;
        }
    }

    private static NativeImage readImage(Minecraft minecraft, Identifier imageId) {
        try {
            if (minecraft == null || minecraft.getResourceManager() == null) {
                return null;
            }

            Resource resource = minecraft.getResourceManager().getResource(imageId).orElse(null);
            if (resource == null) {
                return null;
            }
            try (InputStream input = resource.open()) {
                return NativeImage.read(input);
            }
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Identifier sibling(Identifier jsonId, String file) {
        String path = jsonId.getPath();
        int slash = path.lastIndexOf('/');
        String parent = slash >= 0 ? path.substring(0, slash + 1) : "";
        return Identifier.fromNamespaceAndPath(jsonId.getNamespace(), parent + file);
    }

    private static String slugFrom(Identifier jsonId, int frameWidth, int frameHeight, double fps) {
        String path = jsonId.getPath();
        int slash = path.lastIndexOf('/');
        String fileName = slash >= 0 ? path.substring(slash + 1) : path;
        String baseName = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : fileName;
        String fpsText = fps == Math.rint(fps)
                ? Integer.toString((int) Math.rint(fps))
                : Double.toString(fps).replace('.', '_');
        String exactSuffix = "_" + frameWidth + "x" + frameHeight + "_" + fpsText + "fps";
        String slug;
        if (baseName.endsWith(exactSuffix)) {
            slug = baseName.substring(0, baseName.length() - exactSuffix.length());
        } else {
            slug = baseName.replaceFirst("_\\d+x\\d+_\\d+(?:\\.\\d+)?fps$", "");
        }

        if (slug.equals(baseName)) {
            slug = parentFolder(path);
        }
        if (slug.endsWith("_spritesheets")) {
            slug = slug.substring(0, slug.length() - "_spritesheets".length());
        }
        return slug.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static String parentFolder(String path) {
        int slash = path.lastIndexOf('/');
        if (slash <= 0) {
            return "";
        }
        int previousSlash = path.lastIndexOf('/', slash - 1);
        return previousSlash >= 0 ? path.substring(previousSlash + 1, slash) : path.substring(0, slash);
    }

    private static String biomeSlug(Identifier biomeId) {
        return (biomeId.getNamespace() + "_" + biomeId.getPath())
                .toLowerCase(Locale.ROOT)
                .replace('/', '_')
                .replace('-', '_');
    }

    private static Identifier textureId(Identifier biomeId, String suffix) {
        return Identifier.fromNamespaceAndPath(
                NAMESPACE,
                TEXTURE_PREFIX + "/" + biomeSlug(biomeId) + "/" + suffix
        );
    }

    private static Identifier previewImageId(Identifier biomeId) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, BIOME_PREVIEW_ROOT + "/" + biomeSlug(biomeId) + ".png");
    }

    private static int preferenceScore(AnimationMetadata metadata) {
        if (metadata.frameWidth() == MODERN_FRAME_WIDTH && metadata.frameHeight() == MODERN_FRAME_HEIGHT && Math.round(metadata.fps()) == 20L) {
            return 0;
        }
        if (metadata.frameWidth() == LEGACY_FRAME_WIDTH && metadata.frameHeight() == LEGACY_FRAME_HEIGHT && Math.round(metadata.fps()) == 20L) {
            return 1;
        }
        return 10;
    }

    private boolean isSupportedFrameSize(int frameWidth, int frameHeight) {
        return (frameWidth == MODERN_FRAME_WIDTH && frameHeight == MODERN_FRAME_HEIGHT)
                || (frameWidth == LEGACY_FRAME_WIDTH && frameHeight == LEGACY_FRAME_HEIGHT);
    }

    private static int jsonInt(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : 0;
    }

    private static double jsonDouble(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsDouble() : 0.0D;
    }

    private static String jsonString(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    public record CachedFrame(Identifier textureId, int width, int height) {
    }

    public record CachedSheet(
            Identifier textureId,
            int sheetWidth,
            int sheetHeight,
            int frames,
            int columns,
            int frameWidth,
            int frameHeight,
            double fps
    ) {
        public int frameIndex(long renderMillis) {
            long frameDurationMillis = Math.max(1L, Math.round(1000.0D / this.fps));
            return (int) ((renderMillis / frameDurationMillis) % this.frames);
        }
    }

    private record AnimationMetadata(
            String slug,
            Identifier jsonId,
            Identifier sheetId,
            int frames,
            int columns,
            int rows,
            int frameWidth,
            int frameHeight,
            int sheetWidth,
            int sheetHeight,
            double fps
    ) {
        private boolean fits(int actualSheetWidth, int actualSheetHeight) {
            int expectedWidth = this.sheetWidth > 0 ? this.sheetWidth : this.columns * this.frameWidth;
            int expectedHeight = this.sheetHeight > 0 ? this.sheetHeight : this.rows * this.frameHeight;
            return actualSheetWidth >= expectedWidth
                    && actualSheetHeight >= expectedHeight
                    && actualSheetWidth >= this.columns * this.frameWidth
                    && actualSheetHeight >= this.rows * this.frameHeight;
        }
    }
}
