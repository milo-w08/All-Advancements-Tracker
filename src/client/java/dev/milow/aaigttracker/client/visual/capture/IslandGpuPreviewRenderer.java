package dev.milow.aaigttracker.client.visual.capture;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.milow.aaigttracker.mixin.GuiGraphicsExtractorAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

public final class IslandGpuPreviewRenderer implements AutoCloseable {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final float GUI_DEPTH_CENTER = 500.0F;
    private static final float GUI_DEPTH_SCALE = 4.0F;
    private static final double SIDE_HEIGHT_SCALE = 0.86D;

    private final Minecraft minecraft;
    private IslandBlockMap cachedBlockMap;
    private GpuMeshes cachedMeshes;

    public IslandGpuPreviewRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(
            GuiGraphicsExtractor graphics,
            IslandBlockMap blockMap,
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
        if (this.cachedBlockMap != blockMap) {
            this.cachedBlockMap = blockMap;
            this.cachedMeshes = GpuMeshes.from(this.minecraft, blockMap);
        }

        AbstractTexture atlas = this.minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        var textureSetup = net.minecraft.client.gui.render.TextureSetup.singleTextureWithLightmap(atlas.getTextureView(), atlas.getSampler());
        ScreenRectangle screenRectangle = new ScreenRectangle(x, y, width, height);
        if (!this.cachedMeshes.solid().isEmpty()) {
            ((GuiGraphicsExtractorAccessor) graphics).aaigttracker$guiRenderState().addGuiElement(new IslandPreviewRenderState(
                    this.cachedMeshes.solid(),
                    RenderPipelines.CUTOUT_BLOCK,
                    textureSetup,
                    screenRectangle,
                    yaw,
                    pitch,
                    zoom,
                    panX,
                    panY
            ));
        }
        if (!this.cachedMeshes.fluid().isEmpty()) {
            ((GuiGraphicsExtractorAccessor) graphics).aaigttracker$guiRenderState().addGuiElement(new IslandPreviewRenderState(
                    this.cachedMeshes.fluid(),
                    RenderPipelines.TRANSLUCENT_BLOCK,
                    textureSetup,
                    screenRectangle,
                    yaw,
                    pitch,
                    zoom,
                    panX,
                    panY
            ));
        }
    }

    @Override
    public void close() {
        this.cachedBlockMap = null;
        this.cachedMeshes = null;
    }

    private static BlockState parseBlockState(String text) {
        String blockId = text;
        String properties = "";
        if (text.startsWith("Block{")) {
            int end = text.indexOf('}');
            if (end > 6) {
                blockId = text.substring(6, end);
                if (end + 1 < text.length()) {
                    properties = text.substring(end + 1);
                }
            }
        } else {
            int bracket = text.indexOf('[');
            if (bracket >= 0) {
                blockId = text.substring(0, bracket);
                properties = text.substring(bracket);
            }
        }

        Identifier identifier = Identifier.tryParse(blockId);
        if (identifier == null) {
            return Blocks.AIR.defaultBlockState();
        }

        Block block = BuiltInRegistries.BLOCK.getValue(identifier);
        if (block == null) {
            return Blocks.AIR.defaultBlockState();
        }

        BlockState state = block.defaultBlockState();
        if (properties.startsWith("[") && properties.endsWith("]")) {
            String body = properties.substring(1, properties.length() - 1);
            if (!body.isBlank()) {
                for (String assignment : body.split(",")) {
                    int equals = assignment.indexOf('=');
                    if (equals <= 0 || equals >= assignment.length() - 1) {
                        continue;
                    }
                    Property<?> property = block.getStateDefinition().getProperty(assignment.substring(0, equals));
                    if (property != null) {
                        state = setProperty(state, property, assignment.substring(equals + 1));
                    }
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState setProperty(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(found -> state.setValue(property, found)).orElse(state);
    }

    private record GpuMeshes(GpuMesh solid, GpuMesh fluid) {
        private static GpuMeshes from(Minecraft minecraft, IslandBlockMap blockMap) {
            CapturedBlockView view = new CapturedBlockView(blockMap);
            ModelBlockRenderer blockRenderer = new ModelBlockRenderer(true, true, minecraft.getBlockColors());
            FluidStateModelSet fluidModels = minecraft.getModelManager().getFluidStateModelSet();
            List<TexturedQuad> solidQuads = new ArrayList<>(blockMap.blocks().size() * 3);
            List<TexturedQuad> fluidQuads = new ArrayList<>();
            BlockQuadCollector blockCollector = new BlockQuadCollector(solidQuads);

            for (IslandBlockMap.BlockEntry block : blockMap.blocks()) {
                BlockPos pos = new BlockPos(block.x(), block.y(), block.z());
                BlockState state = view.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }

                if (!(state.getBlock() instanceof LiquidBlock)) {
                    blockRenderer.tesselateBlock(
                            blockCollector,
                            block.x(),
                            block.y(),
                            block.z(),
                            view,
                            pos,
                            state,
                            minecraft.getModelManager().getBlockStateModelSet().get(state),
                            BlockPos.asLong(block.x(), block.y(), block.z())
                    );
                }

                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty() && view.isTopFluid(pos)) {
                    addFluidQuads(minecraft, fluidModels, view, pos, state, fluid, fluidQuads);
                }
            }

            IslandBlockMap.Bounds bounds = blockMap.bounds();
            return new GpuMeshes(new GpuMesh(bounds, solidQuads), new GpuMesh(bounds, fluidQuads));
        }
    }

    private static final class GpuMesh {
        private final IslandBlockMap.Bounds bounds;
        private final float[] x0;
        private final float[] y0;
        private final float[] z0;
        private final float[] u0;
        private final float[] v0;
        private final int[] c0;
        private final float[] x1;
        private final float[] y1;
        private final float[] z1;
        private final float[] u1;
        private final float[] v1;
        private final int[] c1;
        private final float[] x2;
        private final float[] y2;
        private final float[] z2;
        private final float[] u2;
        private final float[] v2;
        private final int[] c2;
        private final float[] x3;
        private final float[] y3;
        private final float[] z3;
        private final float[] u3;
        private final float[] v3;
        private final int[] c3;
        private final float[] sortX;
        private final float[] sortY;
        private final float[] sortZ;

        private GpuMesh(IslandBlockMap.Bounds bounds, List<TexturedQuad> quads) {
            this.bounds = bounds;
            this.x0 = new float[quads.size()];
            this.y0 = new float[quads.size()];
            this.z0 = new float[quads.size()];
            this.u0 = new float[quads.size()];
            this.v0 = new float[quads.size()];
            this.c0 = new int[quads.size()];
            this.x1 = new float[quads.size()];
            this.y1 = new float[quads.size()];
            this.z1 = new float[quads.size()];
            this.u1 = new float[quads.size()];
            this.v1 = new float[quads.size()];
            this.c1 = new int[quads.size()];
            this.x2 = new float[quads.size()];
            this.y2 = new float[quads.size()];
            this.z2 = new float[quads.size()];
            this.u2 = new float[quads.size()];
            this.v2 = new float[quads.size()];
            this.c2 = new int[quads.size()];
            this.x3 = new float[quads.size()];
            this.y3 = new float[quads.size()];
            this.z3 = new float[quads.size()];
            this.u3 = new float[quads.size()];
            this.v3 = new float[quads.size()];
            this.c3 = new int[quads.size()];
            this.sortX = new float[quads.size()];
            this.sortY = new float[quads.size()];
            this.sortZ = new float[quads.size()];

            for (int index = 0; index < quads.size(); index++) {
                TexturedQuad quad = quads.get(index);
                this.x0[index] = quad.x0;
                this.y0[index] = quad.y0;
                this.z0[index] = quad.z0;
                this.u0[index] = quad.u0;
                this.v0[index] = quad.v0;
                this.c0[index] = quad.c0;
                this.x1[index] = quad.x1;
                this.y1[index] = quad.y1;
                this.z1[index] = quad.z1;
                this.u1[index] = quad.u1;
                this.v1[index] = quad.v1;
                this.c1[index] = quad.c1;
                this.x2[index] = quad.x2;
                this.y2[index] = quad.y2;
                this.z2[index] = quad.z2;
                this.u2[index] = quad.u2;
                this.v2[index] = quad.v2;
                this.c2[index] = quad.c2;
                this.x3[index] = quad.x3;
                this.y3[index] = quad.y3;
                this.z3[index] = quad.z3;
                this.u3[index] = quad.u3;
                this.v3[index] = quad.v3;
                this.c3[index] = quad.c3;
                this.sortX[index] = quad.sortX;
                this.sortY[index] = quad.sortY;
                this.sortZ[index] = quad.sortZ;
            }
        }

        private boolean isEmpty() {
            return this.x0.length == 0;
        }
    }

    private static final class BlockQuadCollector implements BlockQuadOutput {
        private final List<TexturedQuad> quads;

        private BlockQuadCollector(List<TexturedQuad> quads) {
            this.quads = quads;
        }

        @Override
        public void put(float x, float y, float z, BakedQuad bakedQuad, QuadInstance instance) {
            int alphaSafe0 = alphaSafe(instance.getColor(0));
            int alphaSafe1 = alphaSafe(instance.getColor(1));
            int alphaSafe2 = alphaSafe(instance.getColor(2));
            int alphaSafe3 = alphaSafe(instance.getColor(3));
            this.quads.add(new TexturedQuad(
                    x + bakedQuad.position(0).x(), y + bakedQuad.position(0).y(), z + bakedQuad.position(0).z(),
                    UVPair.unpackU(bakedQuad.packedUV(0)), UVPair.unpackV(bakedQuad.packedUV(0)), alphaSafe0,
                    x + bakedQuad.position(1).x(), y + bakedQuad.position(1).y(), z + bakedQuad.position(1).z(),
                    UVPair.unpackU(bakedQuad.packedUV(1)), UVPair.unpackV(bakedQuad.packedUV(1)), alphaSafe1,
                    x + bakedQuad.position(2).x(), y + bakedQuad.position(2).y(), z + bakedQuad.position(2).z(),
                    UVPair.unpackU(bakedQuad.packedUV(2)), UVPair.unpackV(bakedQuad.packedUV(2)), alphaSafe2,
                    x + bakedQuad.position(3).x(), y + bakedQuad.position(3).y(), z + bakedQuad.position(3).z(),
                    UVPair.unpackU(bakedQuad.packedUV(3)), UVPair.unpackV(bakedQuad.packedUV(3)), alphaSafe3,
                    x + 0.5F, y + 0.5F, z + 0.5F
            ));
        }
    }

    private static int alphaSafe(int color) {
        return (color & 0xFF000000) == 0 ? color | 0xFF000000 : color;
    }

    private static void addFluidQuads(
            Minecraft minecraft,
            FluidStateModelSet fluidModels,
            CapturedBlockView view,
            BlockPos pos,
            BlockState state,
            FluidState fluid,
            List<TexturedQuad> quads
    ) {
        FluidModel model = fluidModels.get(fluid);
        TextureAtlasSprite still = model.stillMaterial().sprite();
        TextureAtlasSprite flowing = model.flowingMaterial().sprite();
        int color = fluidColor(minecraft, model, view, state, pos);
        float x = pos.getX();
        float y = pos.getY();
        float z = pos.getZ();
        float top = 0.92F;

        addFace(quads, still, color, x + 0.5F, y + 0.5F, z + 0.5F,
                x, y + top, z,
                x, y + top, z + 1,
                x + 1, y + top, z + 1,
                x + 1, y + top, z);
        if (!view.hasTopFluidColumn(pos.getX(), pos.getZ() - 1)) {
            addFace(quads, flowing, darken(color, 0.74F), x + 0.5F, y + 0.5F, z + 0.5F,
                    x + 1, y, z,
                    x, y, z,
                    x, y + top, z,
                    x + 1, y + top, z);
        }
        if (!view.hasTopFluidColumn(pos.getX(), pos.getZ() + 1)) {
            addFace(quads, flowing, darken(color, 0.74F), x + 0.5F, y + 0.5F, z + 0.5F,
                    x, y, z + 1,
                    x + 1, y, z + 1,
                    x + 1, y + top, z + 1,
                    x, y + top, z + 1);
        }
        if (!view.hasTopFluidColumn(pos.getX() - 1, pos.getZ())) {
            addFace(quads, flowing, darken(color, 0.64F), x + 0.5F, y + 0.5F, z + 0.5F,
                    x, y, z,
                    x, y, z + 1,
                    x, y + top, z + 1,
                    x, y + top, z);
        }
        if (!view.hasTopFluidColumn(pos.getX() + 1, pos.getZ())) {
            addFace(quads, flowing, darken(color, 0.64F), x + 0.5F, y + 0.5F, z + 0.5F,
                    x + 1, y, z + 1,
                    x + 1, y, z,
                    x + 1, y + top, z,
                    x + 1, y + top, z + 1);
        }
    }

    private static int fluidColor(Minecraft minecraft, FluidModel model, CapturedBlockView view, BlockState state, BlockPos pos) {
        BlockTintSource tintSource = model.tintSource();
        int rgb = tintSource == null ? 0xFFFFFF : tintSource.colorInWorld(state, view, pos);
        boolean lava = BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()).getPath().contains("lava");
        int alpha = lava ? 0xFF : 0xB8;
        return alpha << 24 | rgb & 0x00FFFFFF;
    }

    private static void addFace(
            List<TexturedQuad> quads,
            TextureAtlasSprite sprite,
            int color,
            float sortX,
            float sortY,
            float sortZ,
            float x0,
            float y0,
            float z0,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3
    ) {
        quads.add(new TexturedQuad(
                x0, y0, z0, sprite.getU0(), sprite.getV0(), color,
                x1, y1, z1, sprite.getU0(), sprite.getV1(), color,
                x2, y2, z2, sprite.getU1(), sprite.getV1(), color,
                x3, y3, z3, sprite.getU1(), sprite.getV0(), color,
                sortX, sortY, sortZ
        ));
    }

    private static int darken(int color, float factor) {
        int alpha = color & 0xFF000000;
        int red = Mth.clamp((int) (((color >> 16) & 255) * factor), 0, 255);
        int green = Mth.clamp((int) (((color >> 8) & 255) * factor), 0, 255);
        int blue = Mth.clamp((int) ((color & 255) * factor), 0, 255);
        return alpha | red << 16 | green << 8 | blue;
    }

    private static final class CapturedBlockView implements BlockAndTintGetter {
        private final IslandBlockMap.Bounds bounds;
        private final Map<Long, BlockState> states;
        private final Map<Long, Integer> topFluidByColumn = new HashMap<>();
        private final String biomeId;

        private CapturedBlockView(IslandBlockMap blockMap) {
            this.bounds = blockMap.bounds();
            this.biomeId = blockMap.biomeId();
            this.states = new HashMap<>(blockMap.blocks().size() * 2);
            BlockState[] palette = blockMap.palette().stream()
                    .map(entry -> parseBlockState(entry.state()))
                    .toArray(BlockState[]::new);
            for (IslandBlockMap.BlockEntry block : blockMap.blocks()) {
                if (block.paletteIndex() >= 0 && block.paletteIndex() < palette.length) {
                    BlockState state = palette[block.paletteIndex()];
                    this.states.put(BlockPos.asLong(block.x(), block.y(), block.z()), state);
                    if (!state.getFluidState().isEmpty()) {
                        long columnKey = columnKey(block.x(), block.z());
                        this.topFluidByColumn.merge(columnKey, block.y(), Math::max);
                    }
                }
            }
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.states.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        private boolean isTopFluid(BlockPos pos) {
            Integer topY = this.topFluidByColumn.get(columnKey(pos.getX(), pos.getZ()));
            return topY != null && topY == pos.getY();
        }

        private boolean hasTopFluidColumn(int x, int z) {
            return this.topFluidByColumn.containsKey(columnKey(x, z));
        }

        @Override
        public int getHeight() {
            return this.bounds.sizeY();
        }

        @Override
        public int getMinY() {
            return this.bounds.minY();
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return null;
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return 15;
        }

        @Override
        public boolean canSeeSky(BlockPos pos) {
            return true;
        }

        @Override
        public CardinalLighting cardinalLighting() {
            return CardinalLighting.DEFAULT;
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return tintForBiome(this.biomeId, colorResolver);
        }

        private static long columnKey(int x, int z) {
            return (long) x << 32 ^ z & 0xFFFFFFFFL;
        }
    }

    private static int tintForBiome(String biomeId, ColorResolver colorResolver) {
        String id = biomeId.toLowerCase();
        if (colorResolver == BiomeColors.WATER_COLOR_RESOLVER) {
            if (id.contains("warm_ocean")) {
                return 0x43D5EE;
            }
            if (id.contains("lukewarm_ocean")) {
                return 0x45ADF2;
            }
            if (id.contains("frozen") || id.contains("cold")) {
                return 0x3D57D6;
            }
            if (id.contains("swamp")) {
                return 0x617B64;
            }
            return 0x3F76E4;
        }
        if (colorResolver == BiomeColors.FOLIAGE_COLOR_RESOLVER || colorResolver == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER) {
            if (id.contains("taiga")) {
                return 0x68A464;
            }
            if (id.contains("jungle")) {
                return 0x30BB0B;
            }
            if (id.contains("dark_forest")) {
                return 0x507A32;
            }
            if (id.contains("desert") || id.contains("badlands")) {
                return 0xAEA42A;
            }
            return 0x48B518;
        }
        if (id.contains("snow") || id.contains("frozen")) {
            return 0x80B497;
        }
        if (id.contains("taiga")) {
            return 0x86B783;
        }
        if (id.contains("jungle")) {
            return 0x59C93C;
        }
        if (id.contains("swamp")) {
            return 0x6A7039;
        }
        if (id.contains("desert") || id.contains("badlands")) {
            return 0xBFB755;
        }
        return 0x91BD59;
    }

    private record TexturedQuad(
            float x0, float y0, float z0, float u0, float v0, int c0,
            float x1, float y1, float z1, float u1, float v1, int c1,
            float x2, float y2, float z2, float u2, float v2, int c2,
            float x3, float y3, float z3, float u3, float v3, int c3,
            float sortX, float sortY, float sortZ
    ) {
    }

    private record ProjectedVertex(float x, float y, float depth) {
    }

    private static final class Projection {
        private final int screenCenterX;
        private final int screenCenterY;
        private final double centerX;
        private final double centerY;
        private final double centerZ;
        private final double sin;
        private final double cos;
        private final double pitch;
        private final double zoom;

        private Projection(IslandBlockMap.Bounds bounds, ScreenRectangle area, double yaw, double pitch, double zoom, double panX, double panY) {
            this.screenCenterX = area.left() + area.width() / 2 + (int) panX;
            this.screenCenterY = area.top() + area.height() / 2 + (int) panY;
            this.centerX = bounds.minX() + bounds.sizeX() / 2.0;
            this.centerY = bounds.minY() + bounds.sizeY() / 2.0;
            this.centerZ = bounds.minZ() + bounds.sizeZ() / 2.0;
            this.sin = Math.sin(yaw);
            this.cos = Math.cos(yaw);
            this.pitch = pitch;
            this.zoom = zoom;
        }

        private ProjectedVertex project(float worldX, float worldY, float worldZ) {
            double x = worldX - this.centerX;
            double y = worldY - this.centerY;
            double z = worldZ - this.centerZ;
            double rx = x * this.cos - z * this.sin;
            double rz = x * this.sin + z * this.cos;
            float sx = (float) (this.screenCenterX + rx * this.zoom);
            float sy = (float) (this.screenCenterY + (rz * this.pitch - y * SIDE_HEIGHT_SCALE) * this.zoom);
            float depth = (float) (rz + y * 0.018);
            return new ProjectedVertex(sx, sy, depth);
        }
    }

    private static final class IslandPreviewRenderState implements GuiElementRenderState {
        private final GpuMesh mesh;
        private final RenderPipeline pipeline;
        private final net.minecraft.client.gui.render.TextureSetup textureSetup;
        private final ScreenRectangle bounds;
        private final Projection projection;
        private final int[] order;
        private final float[] depths;

        private IslandPreviewRenderState(
                GpuMesh mesh,
                RenderPipeline pipeline,
                net.minecraft.client.gui.render.TextureSetup textureSetup,
                ScreenRectangle bounds,
                double yaw,
                double pitch,
                double zoom,
                double panX,
                double panY
        ) {
            this.mesh = mesh;
            this.pipeline = pipeline;
            this.textureSetup = textureSetup;
            this.bounds = bounds;
            this.projection = new Projection(mesh.bounds, bounds, yaw, pitch, zoom, panX, panY);
            this.order = new int[mesh.x0.length];
            this.depths = new float[mesh.x0.length];
            for (int index = 0; index < this.order.length; index++) {
                this.order[index] = index;
                this.depths[index] = this.anchorDepth(index);
            }
            stableSort(this.order, this.depths);
        }

        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int sortedIndex : this.order) {
                ProjectedVertex a = this.projection.project(this.mesh.x0[sortedIndex], this.mesh.y0[sortedIndex], this.mesh.z0[sortedIndex]);
                ProjectedVertex b = this.projection.project(this.mesh.x1[sortedIndex], this.mesh.y1[sortedIndex], this.mesh.z1[sortedIndex]);
                ProjectedVertex c = this.projection.project(this.mesh.x2[sortedIndex], this.mesh.y2[sortedIndex], this.mesh.z2[sortedIndex]);
                ProjectedVertex d = this.projection.project(this.mesh.x3[sortedIndex], this.mesh.y3[sortedIndex], this.mesh.z3[sortedIndex]);
                emit(consumer, a, this.mesh.u0[sortedIndex], this.mesh.v0[sortedIndex], this.mesh.c0[sortedIndex]);
                emit(consumer, b, this.mesh.u1[sortedIndex], this.mesh.v1[sortedIndex], this.mesh.c1[sortedIndex]);
                emit(consumer, c, this.mesh.u2[sortedIndex], this.mesh.v2[sortedIndex], this.mesh.c2[sortedIndex]);
                emit(consumer, d, this.mesh.u3[sortedIndex], this.mesh.v3[sortedIndex], this.mesh.c3[sortedIndex]);
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return this.pipeline;
        }

        @Override
        public net.minecraft.client.gui.render.TextureSetup textureSetup() {
            return this.textureSetup;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return this.bounds;
        }

        @Override
        public ScreenRectangle bounds() {
            return this.bounds;
        }

        private float anchorDepth(int index) {
            double x = this.mesh.sortX[index] - this.projection.centerX;
            double y = this.mesh.sortY[index] - this.projection.centerY;
            double z = this.mesh.sortZ[index] - this.projection.centerZ;
            return (float) (x * this.projection.sin + z * this.projection.cos + y * 0.018);
        }

        private static void emit(VertexConsumer consumer, ProjectedVertex vertex, float u, float v, int color) {
            consumer.addVertex(vertex.x(), vertex.y(), guiDepth(vertex))
                    .setUv(u, v)
                    .setColor(color)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(0.0F, 0.0F, 1.0F);
        }

        private static float guiDepth(ProjectedVertex vertex) {
            return Mth.clamp(GUI_DEPTH_CENTER + vertex.depth() * GUI_DEPTH_SCALE, 1.0F, 999.0F);
        }

        private static void stableSort(int[] order, float[] depths) {
            if (order.length < 2) {
                return;
            }
            int[] scratch = new int[order.length];
            mergeSort(order, scratch, depths, 0, order.length);
        }

        private static void mergeSort(int[] order, int[] scratch, float[] depths, int start, int end) {
            int length = end - start;
            if (length < 2) {
                return;
            }
            int middle = (start + end) >>> 1;
            mergeSort(order, scratch, depths, start, middle);
            mergeSort(order, scratch, depths, middle, end);
            int left = start;
            int right = middle;
            int out = start;
            while (left < middle && right < end) {
                int leftIndex = order[left];
                int rightIndex = order[right];
                if (comesBefore(leftIndex, rightIndex, depths)) {
                    scratch[out++] = leftIndex;
                    left++;
                } else {
                    scratch[out++] = rightIndex;
                    right++;
                }
            }
            while (left < middle) {
                scratch[out++] = order[left++];
            }
            while (right < end) {
                scratch[out++] = order[right++];
            }
            System.arraycopy(scratch, start, order, start, length);
        }

        private static boolean comesBefore(int first, int second, float[] depths) {
            float difference = depths[first] - depths[second];
            return difference < -0.00001F || Math.abs(difference) <= 0.00001F && first <= second;
        }
    }
}
