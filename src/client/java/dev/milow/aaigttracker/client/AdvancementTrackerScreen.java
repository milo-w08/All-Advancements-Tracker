package dev.milow.aaigttracker.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

public final class AdvancementTrackerScreen extends Screen {
    private static final int OUTER_PADDING = 12;
    private static final int HEADER_Y = 12;
    private static final int BOARD_TOP = 40;
    private static final int BOARD_INNER_PADDING = 10;
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_GAP = 6;
    private static final int CONTENT_GAP = 10;
    private static final int TILE_GAP = 6;
    private static final int DETAIL_HEIGHT = 152;
    private static final int DETAIL_INNER_PADDING = 10;
    private static final int TILE_TEXT_LINE_HEIGHT = 9;
    private static final int TILE_TEXT_GAP = 2;
    private static final int MIN_TILE_WIDTH = 132;
    private static final int MIN_TILE_HEIGHT = 40;
    private static final int EMERGENCY_MIN_TILE_WIDTH = 104;
    private static final int MAX_TILE_WIDTH = 220;
    private static final int MAX_TILE_HEIGHT = 46;
    private static final int REQUIREMENT_COLUMN_GAP = 10;
    private static final int MIN_REQUIREMENT_COLUMN_WIDTH = 136;
    private static final int REQUIREMENT_ENTRY_HEIGHT = 18;
    private static final int REQUIREMENT_ENTRY_GAP = 4;
    private static final int REQUIREMENT_HEADER_HEIGHT = 9;
    private static final int REQUIREMENT_HEADER_GAP = 8;
    private static final int REQUIREMENT_SECTION_GAP = 10;
    private static final int REQUIREMENT_GROUP_LABEL_GAP = 5;
    private static final int REQUIREMENT_SCROLLBAR_WIDTH = 6;
    private static final int REQUIREMENT_SCROLLBAR_GAP = 6;
    private static final int REQUIREMENT_SCROLL_STEP = REQUIREMENT_ENTRY_HEIGHT + REQUIREMENT_ENTRY_GAP;
    private static final int REQUIREMENT_ICON_SIZE = 16;
    private static final int REQUIREMENT_ENTITY_SLOT_WIDTH = 28;
    private static final int REQUIREMENT_BIOME_SLOT_WIDTH = 30;
    private static final int REQUIREMENT_ICON_GAP = 5;
    private static final int TILE_SUBTITLE_SCISSOR_HEIGHT = 10;
    private static final int BREEDING_ICON_CYCLE_MILLIS = 1200;
    private static final long ITEM_SCAN_INTERVAL_MILLIS = 1000L;
    private static final int ITEM_SCAN_DEPTH_LIMIT = 8;
    private static final int PREVIEW_PANEL_PADDING = 12;
    private static final float PREVIEW_MAX_WIDTH_RATIO = 0.60F;
    private static final float PREVIEW_MAX_HEIGHT_RATIO = 0.50F;
    private static final long PREVIEW_ROTATION_PERIOD_MILLIS = 8000L;
    private static final float PREVIEW_CAMERA_X_ROTATION_DEGREES = -18.0F;
    private static final int PREVIEW_CLOSE_SIZE = 14;
    private static final int PREVIEW_NAV_WIDTH = 24;
    private static final int PREVIEW_NAV_HEIGHT = 42;
    private static final int PREVIEW_ITEM_BASE_SIZE = 16;
    private static final int PREVIEW_ITEM_TARGET_SIZE = 96;
    private static final double MARQUEE_PIXELS_PER_MILLISECOND = 0.045D;
    private static final int MARQUEE_LOOP_GAP = 20;
    private static final Identifier ITEMS_TAB_ID = Identifier.fromNamespaceAndPath("aaigttracker", "items");
    private static final Identifier SMITHING_WITH_STYLE_ADVANCEMENT_ID =
            Identifier.parse("minecraft:adventure/trim_with_all_exclusive_armor_patterns");
    private static final Identifier RESPAWN_DRAGON_ADVANCEMENT_ID =
            Identifier.parse("minecraft:end/respawn_dragon");
    private static final int TEXT_PRIMARY = 0xFFFFF8EE;
    private static final int TEXT_HEADER = 0xFFFFF0DA;
    private static final int TEXT_SECONDARY = 0xFFDCCAB6;
    private static final int TEXT_MUTED = 0xFFD9C2AA;
    private static final int TEXT_COUNT = 0xFFF3DFC1;
    private static final int TEXT_BODY = 0xFFE7D8C8;
    private static final int TEXT_SUCCESS = 0xFFA5E08B;
    private static final Set<Identifier> DETAILLESS_ADVANCEMENTS = Set.of(
            Identifier.parse("minecraft:nether/distract_piglin"),
            Identifier.parse("minecraft:story/obtain_armor"),
            Identifier.parse("minecraft:story/shiny_gear"),
            Identifier.parse("minecraft:nether/loot_bastion"),
            Identifier.parse("minecraft:adventure/salvage_sherd"),
            Identifier.parse("minecraft:adventure/read_power_of_chiseled_bookshelf"),
            Identifier.parse("minecraft:adventure/heart_transplanter")
    );
    private static final Map<Identifier, ItemCompletionGate> ITEM_COMPLETION_GATES = Map.ofEntries(
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "conduit"),
                    ItemCompletionGate.advancement(Identifier.parse("minecraft:nether/all_effects"))
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "beacon"),
                    ItemCompletionGate.advancement(Identifier.parse("minecraft:nether/create_full_beacon"))
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "netherite"),
                    ItemCompletionGate.advancement(Identifier.parse("minecraft:nether/netherite_armor"))
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "enchanted_golden_apple"),
                    ItemCompletionGate.advancement(Identifier.parse("minecraft:husbandry/balanced_diet"))
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "trident"),
                    ItemCompletionGate.advancement(Identifier.parse("minecraft:adventure/very_very_frightening"))
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "piercing_crossbow"),
                    ItemCompletionGate.advancements(
                            Identifier.parse("minecraft:adventure/two_birds_one_arrow"),
                            Identifier.parse("minecraft:adventure/arbalistic")
                    )
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "potions"),
                    ItemCompletionGate.advancement(Identifier.parse("minecraft:nether/all_effects"))
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "ominous_bottles"),
                    ItemCompletionGate.advancement(Identifier.parse("minecraft:nether/all_effects"))
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "sniffer_eggs"),
                    ItemCompletionGate.criterion(Identifier.parse("minecraft:husbandry/bred_all_animals"), "sniffer")
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "smithing_with_style"),
                    ItemCompletionGate.advancement(SMITHING_WITH_STYLE_ADVANCEMENT_ID)
            ),
            Map.entry(
                    Identifier.fromNamespaceAndPath("aaigttracker", "end_crystals"),
                    ItemCompletionGate.advancement(RESPAWN_DRAGON_ADVANCEMENT_ID)
            )
    );
    private static final Map<String, Component> CAT_VARIANT_SOURCES = Map.ofEntries(
            Map.entry("all_black", Component.literal("Villages and witch huts")),
            Map.entry("black", Component.literal("Villages")),
            Map.entry("british_shorthair", Component.literal("Villages")),
            Map.entry("calico", Component.literal("Villages")),
            Map.entry("jellie", Component.literal("Villages")),
            Map.entry("persian", Component.literal("Villages")),
            Map.entry("ragdoll", Component.literal("Villages")),
            Map.entry("red", Component.literal("Villages")),
            Map.entry("siamese", Component.literal("Villages")),
            Map.entry("tabby", Component.literal("Villages")),
            Map.entry("white", Component.literal("Villages"))
    );
    private static final Map<String, Component> WOLF_VARIANT_SOURCES = Map.ofEntries(
            Map.entry("ashen", Component.literal("Found in Snowy Taiga")),
            Map.entry("black", Component.literal("Found in Old Growth Pine Taiga")),
            Map.entry("chestnut", Component.literal("Found in Old Growth Spruce Taiga")),
            Map.entry("pale", Component.literal("Found in Taiga")),
            Map.entry("rusty", Component.literal("Found in Sparse Jungle")),
            Map.entry("snowy", Component.literal("Found in Grove")),
            Map.entry("spotted", Component.literal("Found in Savanna Plateau")),
            Map.entry("striped", Component.literal("Found in Wooded Badlands")),
            Map.entry("woods", Component.literal("Found in Forest"))
    );
    private static final Map<String, Component> ENTITY_PREVIEW_SOURCES = Map.ofEntries(
            Map.entry("cold", Component.literal("Cold biome frogs")),
            Map.entry("temperate", Component.literal("Swamp frogs")),
            Map.entry("warm", Component.literal("Warm biome frogs")),
            Map.entry("armadillo", Component.literal("Savannas and Badlands")),
            Map.entry("axolotl", Component.literal("Water pools in Lush Caves")),
            Map.entry("bee", Component.literal("Bee nests in flower biomes")),
            Map.entry("camel", Component.literal("Desert villages")),
            Map.entry("cat", Component.literal("Villages and witch huts")),
            Map.entry("chicken", Component.literal("Overworld")),
            Map.entry("cow", Component.literal("Overworld")),
            Map.entry("donkey", Component.literal("Plains and Savannas")),
            Map.entry("fox", Component.literal("Taiga biomes")),
            Map.entry("frog", Component.literal("Swamps and Mangrove Swamps")),
            Map.entry("goat", Component.literal("Snowy slopes and peaks")),
            Map.entry("hoglin", Component.literal("Crimson Forests")),
            Map.entry("horse", Component.literal("Plains and Savannas")),
            Map.entry("llama", Component.literal("Savannas and Windswept Hills")),
            Map.entry("mooshroom", Component.literal("Mushroom Fields")),
            Map.entry("mule", Component.literal("Breed a horse and donkey")),
            Map.entry("nautilus", Component.literal("Warm ocean waters")),
            Map.entry("ocelot", Component.literal("Jungles")),
            Map.entry("panda", Component.literal("Jungle and Bamboo Jungle")),
            Map.entry("pig", Component.literal("Overworld")),
            Map.entry("rabbit", Component.literal("Deserts, snowy biomes, and Flower Forests")),
            Map.entry("sheep", Component.literal("Overworld")),
            Map.entry("sniffer", Component.literal("Hatch a Sniffer Egg")),
            Map.entry("strider", Component.literal("Nether lava seas")),
            Map.entry("turtle", Component.literal("Beaches")),
            Map.entry("wolf", Component.literal("See Wolf Types")),
            Map.entry("blaze", Component.literal("Nether Fortresses")),
            Map.entry("bogged", Component.literal("Swamps and Trial Chambers")),
            Map.entry("breeze", Component.literal("Trial Chambers")),
            Map.entry("camel_husk", Component.literal("Deserts")),
            Map.entry("cave_spider", Component.literal("Mineshaft spawners")),
            Map.entry("creaking", Component.literal("Pale Garden nights")),
            Map.entry("creeper", Component.literal("Night")),
            Map.entry("drowned", Component.literal("Rivers and oceans")),
            Map.entry("elder_guardian", Component.literal("Ocean Monuments")),
            Map.entry("ender_dragon", Component.literal("The End")),
            Map.entry("enderman", Component.literal("Warped Forest and the End")),
            Map.entry("endermite", Component.literal("Spawn from thrown ender pearls, 5% chance")),
            Map.entry("evoker", Component.literal("Woodland Mansions and raids")),
            Map.entry("ghast", Component.literal("Open Nether biomes")),
            Map.entry("guardian", Component.literal("Ocean Monuments")),
            Map.entry("husk", Component.literal("Deserts")),
            Map.entry("magma_cube", Component.literal("Basalt Deltas")),
            Map.entry("parched", Component.literal("Deserts")),
            Map.entry("phantom", Component.literal("After three sleepless nights")),
            Map.entry("piglin", Component.literal("Crimson Forests and Bastions")),
            Map.entry("piglin_brute", Component.literal("Bastions")),
            Map.entry("pillager", Component.literal("Outposts and raids")),
            Map.entry("ravager", Component.literal("Raids")),
            Map.entry("shulker", Component.literal("End Cities")),
            Map.entry("silverfish", Component.literal("Strongholds and infested blocks")),
            Map.entry("skeleton", Component.literal("Night")),
            Map.entry("slime", Component.literal("Swamps and slime chunks")),
            Map.entry("spider", Component.literal("Night")),
            Map.entry("stray", Component.literal("Frozen biomes")),
            Map.entry("vex", Component.literal("Summoned by evokers")),
            Map.entry("vindicator", Component.literal("Woodland Mansions and raids")),
            Map.entry("witch", Component.literal("Swamps and dark areas")),
            Map.entry("wither", Component.literal("Summon it yourself")),
            Map.entry("wither_skeleton", Component.literal("Nether Fortresses")),
            Map.entry("zoglin", Component.literal("Bring a hoglin to the Overworld")),
            Map.entry("zombie", Component.literal("Night")),
            Map.entry("zombie_horse", Component.literal("Night")),
            Map.entry("zombie_nautilus", Component.literal("Ocean ruins and waters")),
            Map.entry("zombie_pigman", Component.literal("Nether")),
            Map.entry("zombie_villager", Component.literal("Night")),
            Map.entry("zombified_piglin", Component.literal("Nether"))
    );
    private static final Map<String, Component> TRIM_TEMPLATE_LOCATIONS = Map.ofEntries(
            Map.entry("rib", Component.literal("Nether Fortress chest, 6.7%")),
            Map.entry("silence", Component.literal("Ancient City chest, 1.25%")),
            Map.entry("snout", Component.literal("Bastion Treasure chest, 8.3%")),
            Map.entry("spire", Component.literal("End City chest, 6.7%")),
            Map.entry("tide", Component.literal("Elder Guardian drop, 20%")),
            Map.entry("vex", Component.literal("Woodland Mansion chest, 50%")),
            Map.entry("ward", Component.literal("Ancient City chest, 5%")),
            Map.entry("wayfinder", Component.literal("Trail Ruins rare loot, 8.3%"))
    );
    private static final List<TrimTemplateObjective> EXCLUSIVE_TRIM_TEMPLATES = List.of(
            new TrimTemplateObjective("rib", Items.RIB_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplateObjective("silence", Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplateObjective("snout", Items.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplateObjective("spire", Items.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplateObjective("tide", Items.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplateObjective("vex", Items.VEX_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplateObjective("ward", Items.WARD_ARMOR_TRIM_SMITHING_TEMPLATE),
            new TrimTemplateObjective("wayfinder", Items.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE)
    );
    private static final Map<String, Component> ITEM_INFO_SOURCES = Map.ofEntries(
            Map.entry("channeling_trident", Component.literal("Enchanting/books/chests/fishing; odds vary by source")),
            Map.entry("piercing_crossbow", Component.literal("Enchanting/books/chests/trades; odds vary by source"))
    );

    private final AdvancementTrackerManager trackerManager;
    private final BiomeAnimationCache biomeAnimationCache = new BiomeAnimationCache();

    private int boardX;
    private int boardY;
    private int boardWidth;
    private int boardHeight;
    private Identifier activeRootId;
    private Identifier selectedAdvancementId;
    private Identifier hoveredSubtitleAdvancementId;
    private long hoveredSubtitleStartedAt;
    private long currentRenderMillis;
    private Identifier requirementScrollAdvancementId;
    private int requirementScrollOffset;
    private List<AdvancementSnapshot> cachedEntries = List.of();
    private List<ItemProgressSnapshot> cachedItemEntries = List.of();
    private List<BoardTab> cachedTabs = List.of();
    private List<TabButton> renderedTabButtons = List.of();
    private List<TileHitbox> renderedTileHitboxes = List.of();
    private List<RequirementHitbox> renderedRequirementHitboxes = List.of();
    private RequirementViewport renderedRequirementViewport;
    private PreviewOverlay previewOverlay;
    private Boolean debugOverlayWasVisible;
    private long nextItemScanAt;
    private final Map<EntityPreviewSpec, LivingEntity> previewEntities = new HashMap<>();

    public AdvancementTrackerScreen(AdvancementTrackerManager trackerManager) {
        super(Component.translatable("screen.aaigttracker.title"));
        this.trackerManager = trackerManager;
    }

    @Override
    protected void init() {
        this.hideDebugOverlay();
        TrackerUiState trackerUiState = AllAdvancementsIgtTrackerClient.getTrackerUiState(this.currentWorldKey());
        if (this.activeRootId == null) {
            this.activeRootId = trackerUiState.lastTrackerRootId();
        }
        if (this.selectedAdvancementId == null) {
            this.selectedAdvancementId = trackerUiState.lastSelectedAdvancementId();
        }
        this.refreshData();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public void removed() {
        this.rememberCurrentState();
        this.restoreDebugOverlay();
        this.biomeAnimationCache.close();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.refreshData();
        this.currentRenderMillis = System.currentTimeMillis();
        this.extractTransparentBackground(guiGraphics);
        guiGraphics.fill(0, 0, this.width, this.height, 0x92321818);

        guiGraphics.text(this.font, this.title, OUTER_PADDING, HEADER_Y, TEXT_HEADER, false);

        Component countComponent = Component.translatable(
                "screen.aaigttracker.complete_count",
                this.trackerManager.getCompletedCount(),
                this.trackerManager.getTotalCount()
        );
        guiGraphics.text(
                this.font,
                countComponent,
                (this.width - this.font.width(countComponent)) / 2,
                HEADER_Y - 1,
                TEXT_COUNT,
                false
        );

        Component currentTimeComponent = Component.literal(
                this.trackerManager.getLastTimer().available()
                        ? formatMillis(this.trackerManager.getLastTimer().millis())
                        : formatMillis(null)
        );
        guiGraphics.text(
                this.font,
                currentTimeComponent,
                (this.width - this.font.width(currentTimeComponent)) / 2,
                HEADER_Y + 10,
                TEXT_SECONDARY,
                false
        );

        Component timerComponent = Component.translatable(this.trackerManager.getLastTimer().sourceName());
        int timerWidth = this.font.width(timerComponent);
        guiGraphics.text(this.font, timerComponent, this.width - OUTER_PADDING - timerWidth, HEADER_Y, TEXT_SECONDARY, false);
        guiGraphics.fill(OUTER_PADDING, BOARD_TOP - 8, this.width - OUTER_PADDING, BOARD_TOP - 7, 0x8CA06D6D);

        this.boardX = OUTER_PADDING;
        this.boardY = BOARD_TOP;
        this.boardWidth = this.width - OUTER_PADDING * 2;
        this.boardHeight = this.height - BOARD_TOP - OUTER_PADDING;

        guiGraphics.fill(this.boardX, this.boardY, this.boardX + this.boardWidth, this.boardY + this.boardHeight, 0x99522A2A);
        guiGraphics.outline(this.boardX, this.boardY, this.boardWidth, this.boardHeight, 0xC48E6565);

        if (this.cachedTabs.isEmpty()) {
            this.renderedTabButtons = List.of();
            this.renderedTileHitboxes = List.of();
            this.renderedRequirementHitboxes = List.of();
            this.renderedRequirementViewport = null;
            Component emptyMessage = this.cachedEntries.isEmpty()
                    ? this.trackerManager.getStatusMessage()
                    : Component.translatable("screen.aaigttracker.filter.empty");
            guiGraphics.centeredText(
                    this.font,
                    emptyMessage,
                    this.boardX + this.boardWidth / 2,
                    this.boardY + this.boardHeight / 2 - 4,
                    TEXT_COUNT
            );
            super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        this.renderedTabButtons = this.renderTabs(guiGraphics, mouseX, mouseY);

        BoardTab activeTab = this.getActiveTab();
        if (activeTab == null) {
            this.renderedRequirementHitboxes = List.of();
            this.renderedRequirementViewport = null;
            super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        int interactionMouseX = this.previewOverlay == null ? mouseX : Integer.MIN_VALUE / 4;
        int interactionMouseY = this.previewOverlay == null ? mouseY : Integer.MIN_VALUE / 4;
        int contentX = this.boardX + BOARD_INNER_PADDING;
        int contentWidth = this.boardWidth - BOARD_INNER_PADDING * 2;
        int tabsBottom = this.boardY + BOARD_INNER_PADDING + TAB_HEIGHT;
        int gridY = tabsBottom + CONTENT_GAP;
        int contentBottom = this.boardY + this.boardHeight - BOARD_INNER_PADDING;
        BoardEntry selectedEntry = this.getSelectedEntry(activeTab);
        int detailHeight = this.detailPanelHeight(selectedEntry);
        GridLayout layout = this.computeGridLayout(
                activeTab.entries().size(),
                contentWidth,
                this.gridHeight(gridY, contentBottom, detailHeight)
        );
        BoardEntry hoveredEntry = this.findHoveredEntry(
                activeTab,
                interactionMouseX,
                interactionMouseY,
                contentX,
                gridY,
                contentWidth,
                this.gridHeight(gridY, contentBottom, detailHeight),
                layout
        );
        BoardEntry detailEntry = hoveredEntry != null ? hoveredEntry : selectedEntry;
        int resolvedDetailHeight = this.detailPanelHeight(detailEntry);
        if (resolvedDetailHeight != detailHeight) {
            detailHeight = resolvedDetailHeight;
            layout = this.computeGridLayout(
                    activeTab.entries().size(),
                    contentWidth,
                    this.gridHeight(gridY, contentBottom, detailHeight)
            );
            hoveredEntry = this.findHoveredEntry(
                    activeTab,
                    interactionMouseX,
                    interactionMouseY,
                    contentX,
                    gridY,
                    contentWidth,
                    this.gridHeight(gridY, contentBottom, detailHeight),
                    layout
            );
            detailEntry = hoveredEntry != null ? hoveredEntry : selectedEntry;
        }

        int gridHeight = this.gridHeight(gridY, contentBottom, detailHeight);
        int detailY = detailHeight > 0 ? contentBottom - detailHeight : contentBottom;
        this.renderedTileHitboxes = new ArrayList<>(activeTab.entries().size());
        HoveredTile hoveredTile = this.renderGrid(
                guiGraphics,
                interactionMouseX,
                interactionMouseY,
                activeTab,
                contentX,
                gridY,
                contentWidth,
                gridHeight,
                layout
        );
        if (hoveredTile == null) {
            this.updateHoveredSubtitleState(null);
        }

        this.renderedRequirementHitboxes = new ArrayList<>();
        if (detailHeight > 0) {
            BoardEntry renderedDetailEntry = hoveredTile != null ? hoveredTile.entry() : detailEntry;
            this.renderDetailPanel(guiGraphics, renderedDetailEntry, contentX, detailY, contentWidth, detailHeight, interactionMouseX, interactionMouseY);
        } else {
            this.renderedRequirementViewport = null;
            this.requirementScrollAdvancementId = null;
            this.requirementScrollOffset = 0;
        }
        if (this.previewOverlay == null) {
            this.renderRequirementTooltip(guiGraphics, mouseX, mouseY);
        } else {
            this.renderPreviewOverlay(guiGraphics);
        }

        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        KeyMapping openTrackerKey = AllAdvancementsIgtTrackerClient.getOpenTrackerKey();
        if (openTrackerKey != null && openTrackerKey.matches(event)) {
            this.minecraft.setScreen(null);
            return true;
        }

        if (this.previewOverlay != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.closePreviewOverlay();
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                this.navigatePreview(-1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                this.navigatePreview(1);
                return true;
            }
            return true;
        }

        KeyMapping cycleVisibilityModeKey = AllAdvancementsIgtTrackerClient.getCycleVisibilityModeKey();
        if (cycleVisibilityModeKey != null && cycleVisibilityModeKey.matches(event)) {
            AllAdvancementsIgtTrackerClient.cycleCompletionVisibilityMode(this.currentWorldKey());
            this.refreshData();
            return true;
        }

        if (key == GLFW.GLFW_KEY_F3) {
            return true;
        }

        if (key == GLFW.GLFW_KEY_LEFT) {
            this.cycleTab(-1);
            return true;
        }

        if (key == GLFW.GLFW_KEY_RIGHT) {
            this.cycleTab(1);
            return true;
        }

        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int tabIndex = key - GLFW.GLFW_KEY_1;
            if (tabIndex < this.cachedTabs.size()) {
                this.selectTab(tabIndex);
                return true;
            }
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.previewOverlay != null) {
            return true;
        }

        if (this.renderedRequirementViewport != null
                && this.renderedRequirementViewport.maxScroll() > 0
                && this.renderedRequirementViewport.contains(mouseX, mouseY)) {
            int delta = (int) Math.round(scrollY * REQUIREMENT_SCROLL_STEP);
            this.requirementScrollOffset = clamp(this.requirementScrollOffset - delta, 0, this.renderedRequirementViewport.maxScroll());
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return super.mouseClicked(event, doubleClick);
        }

        if (this.previewOverlay != null) {
            PreviewLayout layout = this.previewLayout();
            if (layout != null) {
                if (layout.closeButton().contains(event.x(), event.y())) {
                    this.closePreviewOverlay();
                    return true;
                }
                if (this.previewOverlay.hasMultiple() && layout.leftArrow().contains(event.x(), event.y())) {
                    this.navigatePreview(-1);
                    return true;
                }
                if (this.previewOverlay.hasMultiple() && layout.rightArrow().contains(event.x(), event.y())) {
                    this.navigatePreview(1);
                    return true;
                }
            }
            return true;
        }

        for (TabButton button : this.renderedTabButtons) {
            if (inside(event.x(), event.y(), button.x(), button.y(), button.width(), button.height())) {
                this.selectTab(button.index());
                return true;
            }
        }

        for (RequirementHitbox hitbox : this.renderedRequirementHitboxes) {
            if (hitbox.contains(event.x(), event.y()) && this.openPreview(hitbox.criterion())) {
                return true;
            }
        }

        for (TileHitbox tileHitbox : this.renderedTileHitboxes) {
            if (inside(event.x(), event.y(), tileHitbox.x(), tileHitbox.y(), tileHitbox.width(), tileHitbox.height())) {
                this.selectedAdvancementId = tileHitbox.entry().id();
                this.rememberCurrentState();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void refreshData() {
        this.cachedEntries = this.trackerManager.getSnapshots();
        this.refreshItemEntries(false);
        this.cachedTabs = this.buildTabs(this.visibleEntries(this.cachedEntries), this.visibleItemEntries(this.cachedItemEntries));

        if (this.cachedTabs.isEmpty()) {
            this.activeRootId = null;
            this.selectedAdvancementId = null;
            this.rememberCurrentState();
            return;
        }

        boolean activeTabStillExists = this.activeRootId != null
                && this.cachedTabs.stream().anyMatch(tab -> tab.rootId().equals(this.activeRootId));
        if (!activeTabStillExists) {
            this.activeRootId = this.cachedTabs.get(0).rootId();
        }

        BoardTab activeTab = this.getActiveTab();
        boolean selectedStillVisible = this.selectedAdvancementId != null
                && activeTab != null
                && activeTab.entries().stream().anyMatch(entry -> entry.id().equals(this.selectedAdvancementId));
        if (!selectedStillVisible) {
            this.selectedAdvancementId = null;
        }

        this.rememberCurrentState();
    }

    private List<AdvancementSnapshot> visibleEntries(List<AdvancementSnapshot> entries) {
        CompletionVisibilityMode visibilityMode = this.visibilityMode();
        if (!visibilityMode.hidesCompletedAdvancements()) {
            return entries;
        }

        List<AdvancementSnapshot> visibleEntries = new ArrayList<>(entries.size());
        for (AdvancementSnapshot snapshot : entries) {
            if (!snapshot.done()) {
                visibleEntries.add(snapshot);
            }
        }
        return List.copyOf(visibleEntries);
    }

    private List<ItemProgressSnapshot> visibleItemEntries(List<ItemProgressSnapshot> entries) {
        CompletionVisibilityMode visibilityMode = this.visibilityMode();
        List<ItemProgressSnapshot> visibleEntries = new ArrayList<>(entries.size());
        for (ItemProgressSnapshot snapshot : entries) {
            if (this.isItemGateComplete(snapshot.id())) {
                continue;
            }
            if (!visibilityMode.hidesCompletedAdvancements() || !snapshot.done()) {
                visibleEntries.add(snapshot);
            }
        }
        return List.copyOf(visibleEntries);
    }

    private boolean isItemGateComplete(Identifier itemEntryId) {
        ItemCompletionGate gate = ITEM_COMPLETION_GATES.get(itemEntryId);
        return gate != null && gate.isComplete(this.cachedEntries);
    }

    private List<BoardTab> buildTabs(List<AdvancementSnapshot> entries, List<ItemProgressSnapshot> itemEntries) {
        Map<Identifier, List<AdvancementSnapshot>> groupedEntries = new LinkedHashMap<>();
        for (AdvancementSnapshot snapshot : entries) {
            groupedEntries.computeIfAbsent(snapshot.rootId(), ignored -> new ArrayList<>()).add(snapshot);
        }

        List<BoardTab> tabs = new ArrayList<>(groupedEntries.size() + 1);
        for (Map.Entry<Identifier, List<AdvancementSnapshot>> entry : groupedEntries.entrySet()) {
            List<AdvancementSnapshot> snapshots = entry.getValue();
            if (snapshots.isEmpty()) {
                continue;
            }

            List<BoardEntry> boardEntries = new ArrayList<>(snapshots.size());
            for (AdvancementSnapshot snapshot : snapshots) {
                boardEntries.add(new AdvancementBoardEntry(snapshot));
            }

            tabs.add(new BoardTab(
                    entry.getKey(),
                    snapshots.get(0).rootTitle(),
                    snapshots.get(0).rootIcon().copy(),
                    List.copyOf(boardEntries),
                    TabKind.ADVANCEMENTS
            ));
        }

        List<BoardEntry> itemBoardEntries = new ArrayList<>(itemEntries.size());
        for (ItemProgressSnapshot snapshot : itemEntries) {
            itemBoardEntries.add(new ItemBoardEntry(snapshot));
        }

        tabs.add(new BoardTab(
                ITEMS_TAB_ID,
                Component.translatable("screen.aaigttracker.tab.items"),
                Items.BUNDLE.getDefaultInstance(),
                List.copyOf(itemBoardEntries),
                TabKind.ITEMS
        ));

        return List.copyOf(tabs);
    }

    private List<TabButton> renderTabs(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        List<TabButton> buttons = new ArrayList<>(this.cachedTabs.size());
        int cursorX = this.boardX + BOARD_INNER_PADDING;
        int tabY = this.boardY + BOARD_INNER_PADDING;

        for (int index = 0; index < this.cachedTabs.size(); index++) {
            BoardTab tab = this.cachedTabs.get(index);
            String label = tab.title().getString();
            int width = this.font.width(label) + 34;
            boolean active = tab.rootId().equals(this.activeRootId);
            boolean hovered = inside(mouseX, mouseY, cursorX, tabY, width, TAB_HEIGHT);
            int background = active ? 0xD48C6B2A : hovered ? 0xC5724646 : 0x9B603737;
            int border = active ? 0xFFF0D3A3 : 0xB1855C5C;

            guiGraphics.fill(cursorX, tabY, cursorX + width, tabY + TAB_HEIGHT, background);
            guiGraphics.outline(cursorX, tabY, width, TAB_HEIGHT, border);
            if (!tab.icon().isEmpty()) {
                guiGraphics.item(tab.icon(), cursorX + 5, tabY + 2);
            }
            guiGraphics.text(this.font, label, cursorX + 24, tabY + 6, TEXT_PRIMARY, false);
            buttons.add(new TabButton(index, cursorX, tabY, width, TAB_HEIGHT));
            cursorX += width + TAB_GAP;
        }

        String hint = this.visibilityMode().label().getString();
        guiGraphics.text(
                this.font,
                hint,
                this.boardX + this.boardWidth - BOARD_INNER_PADDING - this.font.width(hint),
                tabY + 6,
                TEXT_MUTED,
                false
        );

        return List.copyOf(buttons);
    }

    private HoveredTile renderGrid(
            GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            BoardTab activeTab,
            int contentX,
            int gridY,
            int contentWidth,
            int gridHeight,
            GridLayout layout
    ) {
        HoveredTile hoveredTile = null;
        int gridStartX = contentX + Math.max(0, (contentWidth - layout.totalWidth()) / 2);
        int gridStartY = gridY + Math.max(0, (gridHeight - layout.totalHeight()) / 2);

        for (int index = 0; index < activeTab.entries().size(); index++) {
            BoardEntry entry = activeTab.entries().get(index);
            int row = index / layout.columns();
            int column = index % layout.columns();
            int tileX = gridStartX + column * (layout.tileWidth() + TILE_GAP);
            int tileY = gridStartY + row * (layout.tileHeight() + TILE_GAP);
            this.renderedTileHitboxes.add(new TileHitbox(entry, tileX, tileY, layout.tileWidth(), layout.tileHeight()));
            HoveredTile candidate = this.renderTile(guiGraphics, mouseX, mouseY, entry, tileX, tileY, layout);
            if (candidate != null) {
                hoveredTile = candidate;
            }
        }

        return hoveredTile;
    }

    private HoveredTile renderTile(
            GuiGraphicsExtractor guiGraphics,
            int mouseX,
            int mouseY,
            BoardEntry entry,
            int tileX,
            int tileY,
            GridLayout layout
    ) {
        boolean hovered = inside(mouseX, mouseY, tileX, tileY, layout.tileWidth(), layout.tileHeight());
        boolean selected = entry.id().equals(this.selectedAdvancementId);
        int background = entry.done()
                ? hovered || selected ? 0xE1B68833 : 0xCC967126
                : hovered || selected ? 0xE1825252 : 0xCC6C3D3D;
        int border = hovered ? 0xFFEED0A0 : selected ? 0xFFF8E3B8 : 0xAA4A2626;

        guiGraphics.fill(tileX, tileY, tileX + layout.tileWidth(), tileY + layout.tileHeight(), background);
        guiGraphics.outline(tileX, tileY, layout.tileWidth(), layout.tileHeight(), border);

        float iconScale = layout.iconScale();
        float iconY = tileY + (layout.tileHeight() - layout.iconSize()) / 2.0F;
        var pose = guiGraphics.pose();
        pose.pushMatrix();
        pose.translate(tileX + 7.0F, iconY);
        pose.scale(iconScale, iconScale);
        guiGraphics.item(entry.icon(), 0, 0);
        pose.popMatrix();

        int textX = tileX + layout.iconSize() + 11;
        int textWidth = Math.max(20, tileX + layout.tileWidth() - textX - 6);
        int textTop = tileY + Math.max(5, (layout.tileHeight() - (TILE_TEXT_LINE_HEIGHT * 2 + TILE_TEXT_GAP)) / 2);

        guiGraphics.text(
                this.font,
                this.ellipsize(entry.title().getString(), textWidth),
                textX,
                textTop,
                TEXT_PRIMARY,
                false
        );

        String subtitle = this.tileSubtitle(entry);
        if (!subtitle.isEmpty()) {
            int subtitleY = textTop + TILE_TEXT_LINE_HEIGHT + TILE_TEXT_GAP;
            int subtitleColor = entry.done() ? TEXT_SUCCESS : TEXT_SECONDARY;
            this.drawTileSubtitle(guiGraphics, entry, subtitle, hovered, textX, subtitleY, textWidth, subtitleColor);
        }

        return hovered ? new HoveredTile(entry) : null;
    }

    private void renderDetailPanel(
            GuiGraphicsExtractor guiGraphics,
            BoardEntry entry,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {
        this.renderedRequirementViewport = null;
        guiGraphics.fill(x, y, x + width, y + height, 0xA04A2626);
        guiGraphics.outline(x, y, width, height, 0xC0906969);

        if (entry == null) {
            this.requirementScrollAdvancementId = null;
            this.requirementScrollOffset = 0;
            guiGraphics.centeredText(
                    this.font,
                    Component.translatable("screen.aaigttracker.hover_hint"),
                    x + width / 2,
                    y + height / 2 - 4,
                    TEXT_SECONDARY
            );
            return;
        }

        if (entry instanceof ItemBoardEntry itemEntry) {
            this.renderItemDetailPanel(guiGraphics, itemEntry.snapshot(), x, y, width, height, mouseX, mouseY);
            return;
        }

        AdvancementSnapshot snapshot = ((AdvancementBoardEntry) entry).snapshot();
        if (snapshot.done()) {
            this.requirementScrollAdvancementId = null;
            this.requirementScrollOffset = 0;
            guiGraphics.centeredText(
                    this.font,
                    Component.translatable("screen.aaigttracker.requirements.complete"),
                    x + width / 2,
                    y + height / 2 - 4,
                    TEXT_SUCCESS
            );
            return;
        }

        RequirementMode requirementMode = this.requirementMode(snapshot);
        if (requirementMode == RequirementMode.SINGLE || (entry instanceof AdvancementBoardEntry advancementBoardEntry && DETAILLESS_ADVANCEMENTS.contains(advancementBoardEntry.snapshot().id()))) {
            this.requirementScrollAdvancementId = null;
            this.requirementScrollOffset = 0;
            guiGraphics.centeredText(
                    this.font,
                    Component.translatable("screen.aaigttracker.requirements.single"),
                    x + width / 2,
                    y + height / 2 - 4,
                    TEXT_MUTED
            );
            return;
        }

        Component summary = this.requirementSummary(requirementMode);
        int headerX = x + DETAIL_INNER_PADDING;
        int headerY = y + DETAIL_INNER_PADDING;
        guiGraphics.text(this.font, summary, headerX, headerY, TEXT_COUNT, false);
        if (requirementMode != RequirementMode.ONE_OF) {
            Component counter = Component.literal(snapshot.completedCriteria() + " / " + snapshot.totalCriteria());
            guiGraphics.text(
                    this.font,
                    counter,
                    x + width - DETAIL_INNER_PADDING - this.font.width(counter),
                    headerY,
                    TEXT_MUTED,
                    false
            );
        }

        int listY = headerY + REQUIREMENT_HEADER_HEIGHT + REQUIREMENT_HEADER_GAP;
        int listHeight = y + height - DETAIL_INNER_PADDING - listY;
        if (listHeight <= 0) {
            this.requirementScrollAdvancementId = null;
            this.requirementScrollOffset = 0;
            return;
        }

        this.renderRequirementSections(
                guiGraphics,
                snapshot.id(),
                this.buildRequirementSections(snapshot, requirementMode),
                x + DETAIL_INNER_PADDING,
                listY,
                width - DETAIL_INNER_PADDING * 2,
                listHeight,
                mouseX,
                mouseY
        );
    }

    private void renderItemDetailPanel(
            GuiGraphicsExtractor guiGraphics,
            ItemProgressSnapshot snapshot,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {
        int headerX = x + DETAIL_INNER_PADDING;
        int headerY = y + DETAIL_INNER_PADDING;
        Component counter = Component.literal(snapshot.current() + " / " + snapshot.total());
        int counterWidth = this.font.width(counter);
        int headerWidth = Math.max(16, width - DETAIL_INNER_PADDING * 2 - counterWidth - 8);
        guiGraphics.text(
                this.font,
                this.ellipsize(snapshot.description().getString(), headerWidth),
                headerX,
                headerY,
                TEXT_COUNT,
                false
        );
        guiGraphics.text(
                this.font,
                counter,
                x + width - DETAIL_INNER_PADDING - counterWidth,
                headerY,
                TEXT_MUTED,
                false
        );

        int listY = headerY + REQUIREMENT_HEADER_HEIGHT + REQUIREMENT_HEADER_GAP;
        int listHeight = y + height - DETAIL_INNER_PADDING - listY;
        if (listHeight <= 0) {
            this.requirementScrollAdvancementId = null;
            this.requirementScrollOffset = 0;
            return;
        }

        List<CriterionSnapshot> visibleCriteria = this.visibleRequirementCriteria(snapshot.criteria());
        if (visibleCriteria.isEmpty()) {
            this.requirementScrollAdvancementId = null;
            this.requirementScrollOffset = 0;
            guiGraphics.centeredText(
                    this.font,
                    Component.translatable("screen.aaigttracker.filter.empty"),
                    x + width / 2,
                    y + height / 2 - 4,
                    TEXT_MUTED
            );
            return;
        }

        this.renderRequirementSections(
                guiGraphics,
                snapshot.id(),
                List.of(new RequirementSection(null, visibleCriteria)),
                x + DETAIL_INNER_PADDING,
                listY,
                width - DETAIL_INNER_PADDING * 2,
                listHeight,
                mouseX,
                mouseY
        );
    }

    private void renderRequirementSections(
            GuiGraphicsExtractor guiGraphics,
            Identifier entryId,
            List<RequirementSection> sections,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {
        if (sections.isEmpty() || height <= 0 || width <= 0) {
            this.requirementScrollAdvancementId = null;
            this.requirementScrollOffset = 0;
            return;
        }

        RequirementRenderData renderData = this.createRequirementRenderData(sections, width, height);
        this.prepareRequirementScroll(entryId, renderData.overflowHeight());
        this.renderedRequirementViewport = new RequirementViewport(x, y, width, height, renderData.overflowHeight());

        guiGraphics.enableScissor(x, y, x + renderData.contentWidth(), y + height);
        int cursorY = y - this.requirementScrollOffset;
        for (int index = 0; index < renderData.layouts().size(); index++) {
            RequirementSectionLayout layout = renderData.layouts().get(index);
            this.renderRequirementSection(guiGraphics, layout, x, cursorY, mouseX, mouseY);
            cursorY += layout.height();
            if (index + 1 < renderData.layouts().size()) {
                cursorY += REQUIREMENT_SECTION_GAP;
            }
        }
        guiGraphics.disableScissor();

        if (renderData.overflowHeight() > 0) {
            this.renderRequirementScrollbar(
                    guiGraphics,
                    x + width - REQUIREMENT_SCROLLBAR_WIDTH,
                    y,
                    REQUIREMENT_SCROLLBAR_WIDTH,
                    height,
                    renderData.contentHeight(),
                    this.requirementScrollOffset
            );
        }
    }

    private void renderRequirementSection(
            GuiGraphicsExtractor guiGraphics,
            RequirementSectionLayout layout,
            int x,
            int y,
            int mouseX,
            int mouseY
    ) {
        int contentY = y;
        if (layout.section().label() != null) {
            guiGraphics.text(this.font, layout.section().label(), x, y, TEXT_MUTED, false);
            contentY += REQUIREMENT_HEADER_HEIGHT + REQUIREMENT_GROUP_LABEL_GAP;
        }

        int rowStep = REQUIREMENT_ENTRY_HEIGHT + REQUIREMENT_ENTRY_GAP;
        for (int index = 0; index < layout.section().criteria().size(); index++) {
            int column = index % layout.columns();
            int row = index / layout.columns();
            int lineX = x + column * (layout.columnWidth() + REQUIREMENT_COLUMN_GAP);
            int lineY = contentY + row * rowStep;
            CriterionSnapshot criterion = layout.section().criteria().get(index);
            boolean hovered = this.renderedRequirementViewport != null
                    && this.renderedRequirementViewport.contains(mouseX, mouseY)
                    && inside(mouseX, mouseY, lineX, lineY, layout.columnWidth(), REQUIREMENT_ENTRY_HEIGHT);
            int background = criterion.completed()
                    ? (hovered ? 0xBC416A36 : 0xA0355A2E)
                    : (hovered ? 0x945F3434 : 0x7D4F2B2B);
            int border = hovered
                    ? (this.isPreviewable(criterion) ? 0xFFF0D3A3 : 0xD4B48A8A)
                    : (criterion.completed() ? 0xD46EBD72 : 0xB18C6767);
            int textColor = criterion.completed() ? TEXT_SUCCESS : TEXT_BODY;
            guiGraphics.fill(lineX, lineY, lineX + layout.columnWidth(), lineY + REQUIREMENT_ENTRY_HEIGHT, background);
            guiGraphics.outline(lineX, lineY, layout.columnWidth(), REQUIREMENT_ENTRY_HEIGHT, border);

            int textX = lineX + 6;
            int primaryIconX = 0;
            int primaryIconWidth = 0;
            if (!criterion.icon().isEmpty()) {
                primaryIconX = lineX + 3;
                primaryIconWidth = this.primaryIconSlotWidth(criterion.icon());
                this.renderCriterionIcon(guiGraphics, criterion.icon(), primaryIconX, lineY + 1, primaryIconWidth, REQUIREMENT_ICON_SIZE);
                textX = primaryIconX + primaryIconWidth + REQUIREMENT_ICON_GAP;
            }

            if (criterion.supplementaryIconMode() == SupplementaryIconMode.STATIC_ALL) {
                for (CriterionIcon supplementaryIcon : criterion.supplementaryIcons()) {
                    if (supplementaryIcon == null || supplementaryIcon.isEmpty()) {
                        continue;
                    }
                    this.renderCriterionIcon(guiGraphics, supplementaryIcon, textX, lineY + 1, REQUIREMENT_ICON_SIZE, REQUIREMENT_ICON_SIZE);
                    textX += REQUIREMENT_ICON_SIZE + REQUIREMENT_ICON_GAP;
                }
            } else {
                CriterionIcon supplementaryIcon = this.currentSupplementaryIcon(criterion);
                if (supplementaryIcon != null && !supplementaryIcon.isEmpty()) {
                    this.renderCriterionIcon(guiGraphics, supplementaryIcon, textX, lineY + 1, REQUIREMENT_ICON_SIZE, REQUIREMENT_ICON_SIZE);
                    textX += REQUIREMENT_ICON_SIZE + REQUIREMENT_ICON_GAP;
                }
            }

            int textWidth = Math.max(12, layout.columnWidth() - (textX - lineX) - 6);
            guiGraphics.text(
                    this.font,
                    this.ellipsize(criterion.displayName().getString(), textWidth),
                    textX,
                    lineY + 5,
                    textColor,
                    false
            );
            this.renderedRequirementHitboxes.add(
                    new RequirementHitbox(
                            criterion,
                            lineX,
                            lineY,
                            layout.columnWidth(),
                            REQUIREMENT_ENTRY_HEIGHT,
                            primaryIconX,
                            lineY + 1,
                            primaryIconWidth,
                            primaryIconWidth > 0 ? REQUIREMENT_ICON_SIZE : 0
                    )
            );
        }
    }

    private List<RequirementSection> buildRequirementSections(AdvancementSnapshot snapshot, RequirementMode requirementMode) {
        if (requirementMode == RequirementMode.ALL_OF) {
            List<CriterionSnapshot> visibleCriteria = this.visibleRequirementCriteria(snapshot.criteria());
            return visibleCriteria.isEmpty()
                    ? List.of()
                    : List.of(new RequirementSection(null, visibleCriteria));
        }

        if (requirementMode == RequirementMode.ONE_OF) {
            if (snapshot.requirementGroups().isEmpty()) {
                return List.of();
            }

            List<CriterionSnapshot> visibleCriteria = this.visibleRequirementCriteria(
                    snapshot.requirementGroups().get(0).criteria()
            );
            return visibleCriteria.isEmpty()
                    ? List.of()
                    : List.of(new RequirementSection(null, visibleCriteria));
        }

        List<RequirementSection> sections = new ArrayList<>(snapshot.requirementGroups().size());
        for (RequirementGroupSnapshot group : snapshot.requirementGroups()) {
            List<CriterionSnapshot> visibleCriteria = this.visibleRequirementCriteria(group.criteria());
            if (visibleCriteria.isEmpty()) {
                continue;
            }

            Component label = group.criteria().size() > 1
                    ? Component.translatable("screen.aaigttracker.requirements.one_of_group")
                    : Component.translatable("screen.aaigttracker.requirements.required_group");
            sections.add(new RequirementSection(label, visibleCriteria));
        }
        return List.copyOf(sections);
    }

    private List<CriterionSnapshot> visibleRequirementCriteria(List<CriterionSnapshot> criteria) {
        if (!this.visibilityMode().hidesCompletedCriteria()) {
            return criteria;
        }

        List<CriterionSnapshot> visibleCriteria = new ArrayList<>(criteria.size());
        for (CriterionSnapshot criterion : criteria) {
            if (!criterion.completed()) {
                visibleCriteria.add(criterion);
            }
        }
        return List.copyOf(visibleCriteria);
    }

    private RequirementSectionLayout createRequirementSectionLayout(RequirementSection section, int width) {
        int columns = this.requirementColumnCount(section.criteria().size(), width);
        int columnWidth = (width - Math.max(0, columns - 1) * REQUIREMENT_COLUMN_GAP) / columns;
        int rows = Math.max(1, (section.criteria().size() + columns - 1) / columns);
        int contentHeight = rows * (REQUIREMENT_ENTRY_HEIGHT + REQUIREMENT_ENTRY_GAP) - REQUIREMENT_ENTRY_GAP;
        int height = contentHeight;
        if (section.label() != null) {
            height += REQUIREMENT_HEADER_HEIGHT + REQUIREMENT_GROUP_LABEL_GAP;
        }

        return new RequirementSectionLayout(section, columns, rows, columnWidth, height);
    }

    private RequirementRenderData createRequirementRenderData(List<RequirementSection> sections, int width, int height) {
        List<RequirementSectionLayout> layouts = this.createRequirementLayouts(sections, width);
        int contentHeight = this.requirementContentHeight(layouts);
        if (contentHeight <= height) {
            return new RequirementRenderData(List.copyOf(layouts), width, contentHeight, 0);
        }

        int contentWidth = Math.max(1, width - REQUIREMENT_SCROLLBAR_WIDTH - REQUIREMENT_SCROLLBAR_GAP);
        layouts = this.createRequirementLayouts(sections, contentWidth);
        contentHeight = this.requirementContentHeight(layouts);
        return new RequirementRenderData(
                List.copyOf(layouts),
                contentWidth,
                contentHeight,
                Math.max(0, contentHeight - height)
        );
    }

    private List<RequirementSectionLayout> createRequirementLayouts(List<RequirementSection> sections, int width) {
        List<RequirementSectionLayout> layouts = new ArrayList<>(sections.size());
        for (RequirementSection section : sections) {
            layouts.add(this.createRequirementSectionLayout(section, width));
        }
        return layouts;
    }

    private int requirementContentHeight(List<RequirementSectionLayout> layouts) {
        int contentHeight = 0;
        for (RequirementSectionLayout layout : layouts) {
            contentHeight += layout.height();
        }
        if (!layouts.isEmpty()) {
            contentHeight += Math.max(0, layouts.size() - 1) * REQUIREMENT_SECTION_GAP;
        }
        return contentHeight;
    }

    private int requirementColumnCount(int itemCount, int width) {
        if (itemCount <= 1 || width <= MIN_REQUIREMENT_COLUMN_WIDTH) {
            return 1;
        }

        return Math.max(1, Math.min(itemCount, (width + REQUIREMENT_COLUMN_GAP) / (MIN_REQUIREMENT_COLUMN_WIDTH + REQUIREMENT_COLUMN_GAP)));
    }

    private RequirementMode requirementMode(AdvancementSnapshot snapshot) {
        if (snapshot.totalCriteria() <= 1 || snapshot.requirementGroups().isEmpty()) {
            return RequirementMode.SINGLE;
        }

        boolean hasMultiOptionGroup = snapshot.requirementGroups().stream().anyMatch(group -> group.criteria().size() > 1);
        if (!hasMultiOptionGroup) {
            return RequirementMode.ALL_OF;
        }

        if (snapshot.requirementGroups().size() == 1) {
            return RequirementMode.ONE_OF;
        }

        return RequirementMode.GROUPED;
    }

    private Component requirementSummary(RequirementMode requirementMode) {
        return switch (requirementMode) {
            case ONE_OF -> Component.translatable("screen.aaigttracker.requirements.one_of");
            case ALL_OF -> Component.translatable("screen.aaigttracker.requirements.all_of");
            case GROUPED -> Component.translatable("screen.aaigttracker.requirements.grouped");
            case SINGLE -> Component.empty();
        };
    }

    private CompletionVisibilityMode visibilityMode() {
        return AllAdvancementsIgtTrackerClient.getCompletionVisibilityMode(this.currentWorldKey());
    }

    private void rememberCurrentState() {
        AllAdvancementsIgtTrackerClient.rememberTrackerState(
                this.currentWorldKey(),
                this.visibilityMode(),
                this.activeRootId,
                this.selectedAdvancementId
        );
    }

    private WorldKey currentWorldKey() {
        return this.trackerManager.getCurrentWorldKey();
    }

    private void hideDebugOverlay() {
        if (this.debugOverlayWasVisible != null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            this.debugOverlayWasVisible = Boolean.FALSE;
            return;
        }

        this.debugOverlayWasVisible = minecraft.debugEntries.isOverlayVisible();
        if (minecraft.debugEntries.isOverlayVisible()) {
            minecraft.debugEntries.setOverlayVisible(false);
        }
    }

    private void restoreDebugOverlay() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && Boolean.TRUE.equals(this.debugOverlayWasVisible)) {
            minecraft.debugEntries.setOverlayVisible(true);
        }
    }

    private void cycleTab(int offset) {
        if (this.cachedTabs.isEmpty()) {
            return;
        }

        int currentIndex = this.activeTabIndex();
        int nextIndex = Math.floorMod(currentIndex + offset, this.cachedTabs.size());
        this.selectTab(nextIndex);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= this.cachedTabs.size()) {
            return;
        }

        this.activeRootId = this.cachedTabs.get(index).rootId();
        this.selectedAdvancementId = null;
        if (ITEMS_TAB_ID.equals(this.activeRootId)) {
            this.nextItemScanAt = 0L;
            this.refreshItemEntries(true);
        }
        this.rememberCurrentState();
    }

    private int activeTabIndex() {
        for (int index = 0; index < this.cachedTabs.size(); index++) {
            if (this.cachedTabs.get(index).rootId().equals(this.activeRootId)) {
                return index;
            }
        }

        return 0;
    }

    private BoardTab getActiveTab() {
        if (this.activeRootId == null) {
            return this.cachedTabs.isEmpty() ? null : this.cachedTabs.get(0);
        }

        for (BoardTab tab : this.cachedTabs) {
            if (tab.rootId().equals(this.activeRootId)) {
                return tab;
            }
        }

        return this.cachedTabs.isEmpty() ? null : this.cachedTabs.get(0);
    }

    private BoardEntry getSelectedEntry(BoardTab activeTab) {
        if (activeTab == null || this.selectedAdvancementId == null) {
            return null;
        }

        for (BoardEntry entry : activeTab.entries()) {
            if (entry.id().equals(this.selectedAdvancementId)) {
                return entry;
            }
        }

        return null;
    }

    private int gridHeight(int gridY, int contentBottom, int detailHeight) {
        int reserved = detailHeight > 0 ? detailHeight + CONTENT_GAP : 0;
        return Math.max(1, contentBottom - gridY - reserved);
    }

    private int detailPanelHeight(BoardEntry entry) {
        return DETAIL_HEIGHT;
    }

    private BoardEntry findHoveredEntry(
            BoardTab activeTab,
            int mouseX,
            int mouseY,
            int contentX,
            int gridY,
            int contentWidth,
            int gridHeight,
            GridLayout layout
    ) {
        int gridStartX = contentX + Math.max(0, (contentWidth - layout.totalWidth()) / 2);
        int gridStartY = gridY + Math.max(0, (gridHeight - layout.totalHeight()) / 2);
        for (int index = 0; index < activeTab.entries().size(); index++) {
            BoardEntry entry = activeTab.entries().get(index);
            int row = index / layout.columns();
            int column = index % layout.columns();
            int tileX = gridStartX + column * (layout.tileWidth() + TILE_GAP);
            int tileY = gridStartY + row * (layout.tileHeight() + TILE_GAP);
            if (inside(mouseX, mouseY, tileX, tileY, layout.tileWidth(), layout.tileHeight())) {
                return entry;
            }
        }

        return null;
    }

    private GridLayout computeGridLayout(int entryCount, int availableWidth, int availableHeight) {
        if (entryCount <= 0) {
            return this.createGridLayout(1, 1, availableWidth, availableHeight);
        }

        int widthLimitedColumns = Math.max(
                1,
                Math.min(entryCount, (availableWidth + TILE_GAP) / (EMERGENCY_MIN_TILE_WIDTH + TILE_GAP))
        );

        for (int columns = widthLimitedColumns; columns >= 1; columns--) {
            int rows = (entryCount + columns - 1) / columns;
            int rawTileWidth = (availableWidth - Math.max(0, columns - 1) * TILE_GAP) / columns;
            int rawTileHeight = (availableHeight - Math.max(0, rows - 1) * TILE_GAP) / rows;
            if (rawTileWidth >= MIN_TILE_WIDTH && rawTileHeight >= MIN_TILE_HEIGHT) {
                return this.createGridLayout(columns, rows, rawTileWidth, rawTileHeight);
            }
        }

        for (int columns = widthLimitedColumns; columns >= 1; columns--) {
            int rows = (entryCount + columns - 1) / columns;
            int rawTileWidth = (availableWidth - Math.max(0, columns - 1) * TILE_GAP) / columns;
            int rawTileHeight = (availableHeight - Math.max(0, rows - 1) * TILE_GAP) / rows;
            if (rawTileWidth > 0 && rawTileHeight > 0) {
                return this.createGridLayout(columns, rows, rawTileWidth, rawTileHeight);
            }
        }

        return this.createGridLayout(1, entryCount, availableWidth, Math.max(1, availableHeight / entryCount));
    }

    private GridLayout createGridLayout(int columns, int rows, int rawTileWidth, int rawTileHeight) {
        int tileWidth = Math.max(1, Math.min(MAX_TILE_WIDTH, rawTileWidth));
        int tileHeight = Math.max(1, Math.min(MAX_TILE_HEIGHT, rawTileHeight));
        int iconSize = Math.max(10, Math.min(18, tileHeight - 14));
        return new GridLayout(columns, rows, tileWidth, tileHeight, iconSize / 16.0F, iconSize);
    }

    private String tileSubtitle(BoardEntry entry) {
        if (entry instanceof AdvancementBoardEntry advancementEntry) {
            AdvancementSnapshot snapshot = advancementEntry.snapshot();
            if (snapshot.done()) {
                return snapshot.hasKnownCompletionTime()
                        ? formatMillis(snapshot.completionTimeMillis())
                        : Component.translatable("screen.aaigttracker.tile.done").getString();
            }

            return this.singleLineText(snapshot.description().getString());
        }

        if (entry instanceof ItemBoardEntry itemEntry) {
            ItemProgressSnapshot snapshot = itemEntry.snapshot();
            return snapshot.current() + "/" + snapshot.total();
        }

        return "";
    }

    private void drawTileSubtitle(
            GuiGraphicsExtractor guiGraphics,
            BoardEntry entry,
            String subtitle,
            boolean hovered,
            int x,
            int y,
            int width,
            int color
    ) {
        if (!(entry instanceof AdvancementBoardEntry advancementEntry)) {
            guiGraphics.text(this.font, this.ellipsize(subtitle, width), x, y, color, false);
            return;
        }

        if (hovered) {
            this.updateHoveredSubtitleState(advancementEntry.snapshot().id());
        }

        int renderedWidth = this.font.width(subtitle);
        if (!hovered || renderedWidth <= width) {
            guiGraphics.text(this.font, this.ellipsize(subtitle, width), x, y, color, false);
            return;
        }

        int cycleWidth = renderedWidth + MARQUEE_LOOP_GAP;
        int offset = this.computeMarqueeOffset(cycleWidth);
        guiGraphics.enableScissor(x, y, x + width, y + TILE_SUBTITLE_SCISSOR_HEIGHT);
        guiGraphics.text(this.font, subtitle, x - offset, y, color, false);
        guiGraphics.text(this.font, subtitle, x - offset + cycleWidth, y, color, false);
        guiGraphics.disableScissor();
    }

    private void updateHoveredSubtitleState(Identifier hoveredId) {
        if (!Objects.equals(this.hoveredSubtitleAdvancementId, hoveredId)) {
            this.hoveredSubtitleAdvancementId = hoveredId;
            this.hoveredSubtitleStartedAt = this.currentRenderMillis;
        }
    }

    private int computeMarqueeOffset(int cycleWidth) {
        if (cycleWidth <= 0) {
            return 0;
        }

        long elapsedMillis = Math.max(0L, this.currentRenderMillis - this.hoveredSubtitleStartedAt);
        return Math.floorMod((int) Math.round(elapsedMillis * MARQUEE_PIXELS_PER_MILLISECOND), cycleWidth);
    }

    private void prepareRequirementScroll(Identifier advancementId, int maxScroll) {
        if (!Objects.equals(this.requirementScrollAdvancementId, advancementId)) {
            this.requirementScrollAdvancementId = advancementId;
            this.requirementScrollOffset = 0;
        }

        this.requirementScrollOffset = clamp(this.requirementScrollOffset, 0, maxScroll);
    }

    private void renderRequirementTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        for (RequirementHitbox hitbox : this.renderedRequirementHitboxes) {
            if (!hitbox.criterion().showCompletionTooltip()
                    || !hitbox.criterion().completed()
                    || !hitbox.contains(mouseX, mouseY)) {
                continue;
            }

            List<Component> tooltip = new ArrayList<>(2);
            tooltip.add(hitbox.criterion().displayName());
            tooltip.add(hitbox.criterion().hasKnownCompletionTime()
                    ? Component.translatable(
                            "screen.aaigttracker.requirement.finished_at",
                            formatMillis(hitbox.criterion().completionTimeMillis())
                    )
                    : Component.translatable("screen.aaigttracker.requirement.finished_unknown"));
            guiGraphics.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
            return;
        }
    }

    private void renderCriterionIcon(GuiGraphicsExtractor guiGraphics, CriterionIcon icon, int x, int y, int slotWidth, int slotHeight) {
        if (icon == null || icon.isEmpty()) {
            return;
        }

        if (icon.isBiomePreview()) {
            this.renderBiomePreview(guiGraphics, icon.biomePreviewSpec(), x, y, slotWidth, slotHeight, false);
            return;
        }

        if (icon.isEntityPreview()) {
            LivingEntity entity = this.previewEntity(icon.entityPreviewSpec());
            if (entity != null) {
                this.renderEntityPortrait(guiGraphics, entity, x, y, slotWidth, slotHeight, false);
                return;
            }
        }

        if (icon.isItem()) {
            int itemX = x + Math.max(0, (slotWidth - REQUIREMENT_ICON_SIZE) / 2);
            int itemY = y + Math.max(0, (slotHeight - REQUIREMENT_ICON_SIZE) / 2);
            guiGraphics.item(icon.copyItemStack(), itemX, itemY);
        }
    }

    private CriterionIcon currentSupplementaryIcon(CriterionSnapshot criterion) {
        if (criterion == null || criterion.supplementaryIcons().isEmpty()) {
            return null;
        }

        if (criterion.supplementaryIconMode() != SupplementaryIconMode.CYCLE_ONE) {
            return criterion.supplementaryIcons().get(0);
        }

        int index = criterion.supplementaryIcons().size() == 1
                ? 0
                : (int) ((this.currentRenderMillis / BREEDING_ICON_CYCLE_MILLIS) % criterion.supplementaryIcons().size());
        return criterion.supplementaryIcons().get(index);
    }

    private int primaryIconSlotWidth(CriterionIcon icon) {
        if (icon != null && icon.isBiomePreview()) {
            return REQUIREMENT_BIOME_SLOT_WIDTH;
        }
        if (icon == null || !icon.isEntityPreview()) {
            return REQUIREMENT_ICON_SIZE;
        }

        return REQUIREMENT_ENTITY_SLOT_WIDTH;
    }

    private boolean openPreview(CriterionSnapshot criterion) {
        if (!this.isPreviewable(criterion)) {
            return false;
        }

        List<CriterionSnapshot> previewableCriteria = new ArrayList<>();
        int selectedIndex = 0;
        for (RequirementHitbox hitbox : this.renderedRequirementHitboxes) {
            CriterionSnapshot candidate = hitbox.criterion();
            if (!this.isPreviewable(candidate)) {
                continue;
            }
            if (candidate == criterion) {
                selectedIndex = previewableCriteria.size();
            }
            previewableCriteria.add(candidate);
        }
        if (previewableCriteria.isEmpty()) {
            previewableCriteria = List.of(criterion);
        }

        this.previewOverlay = new PreviewOverlay(previewableCriteria, selectedIndex);
        this.syncPreviewVideoCache();
        return true;
    }

    private boolean isPreviewable(CriterionSnapshot criterion) {
        if (criterion == null || criterion.icon() == null || criterion.icon().isEmpty()) {
            return false;
        }

        Component subtitle = this.previewSubtitle(criterion);
        boolean entityPreview = criterion.icon().isEntityPreview();
        boolean biomePreview = criterion.icon().isBiomePreview();
        boolean itemPreview = criterion.icon().isItem() && subtitle != null;
        return entityPreview || biomePreview || itemPreview;
    }

    private Component previewSubtitle(CriterionSnapshot criterion) {
        if (criterion == null) {
            return null;
        }

        String key = stripNamespace(criterion.key());
        if (criterion.icon() != null && criterion.icon().isEntityPreview()) {
            EntityPreviewSpec spec = criterion.icon().entityPreviewSpec();
            return switch (spec.variantType()) {
                case CAT -> CAT_VARIANT_SOURCES.get(key);
                case WOLF -> WOLF_VARIANT_SOURCES.get(key);
                case FROG -> ENTITY_PREVIEW_SOURCES.get(key);
                case NONE -> {
                    Component criterionSpecific = ENTITY_PREVIEW_SOURCES.get(key);
                    yield criterionSpecific != null ? criterionSpecific : ENTITY_PREVIEW_SOURCES.get(spec.entityId().getPath());
                }
            };
        }

        Component itemInfo = ITEM_INFO_SOURCES.get(key);
        if (itemInfo != null) {
            return itemInfo;
        }

        String trimPattern = trimPatternKey(criterion.key());
        return trimPattern == null ? null : TRIM_TEMPLATE_LOCATIONS.get(trimPattern);
    }

    private void renderPreviewOverlay(GuiGraphicsExtractor guiGraphics) {
        if (this.previewOverlay == null) {
            return;
        }

        CriterionSnapshot criterion = this.previewOverlay.current();
        if (criterion == null) {
            this.closePreviewOverlay();
            return;
        }

        Component title = criterion.displayName();
        Component subtitle = this.previewSubtitle(criterion);
        CriterionIcon icon = criterion.icon();
        PreviewLayout layout = this.previewLayout();
        if (layout == null) {
            return;
        }

        guiGraphics.fill(0, 0, this.width, this.height, 0x9A160A0A);
        guiGraphics.fill(layout.panelX(), layout.panelY(), layout.panelRight(), layout.panelBottom(), 0xE64A2626);
        guiGraphics.outline(layout.panelX(), layout.panelY(), layout.panelWidth(), layout.panelHeight(), 0xFFF0D3A3);
        guiGraphics.centeredText(this.font, title, this.width / 2, layout.panelY() + 5, TEXT_HEADER);
        if (subtitle != null) {
            guiGraphics.centeredText(this.font, subtitle, this.width / 2, layout.panelY() + 15, TEXT_MUTED);
        }

        if (icon.isEntityPreview()) {
            LivingEntity entity = this.previewEntity(icon.entityPreviewSpec());
            if (entity != null) {
                this.renderEntityPortrait(guiGraphics, entity, layout.visualX(), layout.visualY(), layout.visualWidth(), layout.visualHeight(), true);
            }
        } else if (icon.isBiomePreview()) {
            this.renderBiomePreview(
                    guiGraphics,
                    icon.biomePreviewSpec(),
                    layout.visualX(),
                    layout.visualY(),
                    layout.visualWidth(),
                    layout.visualHeight(),
                    true
            );
        } else if (icon.isItem()) {
            int targetItemSize = Math.min(Math.min(layout.visualWidth(), layout.visualHeight()), PREVIEW_ITEM_TARGET_SIZE);
            float itemScale = targetItemSize / (float) PREVIEW_ITEM_BASE_SIZE;
            int itemX = layout.panelX() + (layout.panelWidth() - targetItemSize) / 2;
            int itemY = layout.visualY() + Math.max(0, (layout.visualHeight() - targetItemSize) / 2);
            var pose = guiGraphics.pose();
            pose.pushMatrix();
            pose.translate(itemX, itemY);
            pose.scale(itemScale, itemScale);
            guiGraphics.item(icon.copyItemStack(), 0, 0);
            pose.popMatrix();
        }

        this.renderPreviewControl(guiGraphics, layout.closeButton(), "X");
        if (this.previewOverlay.hasMultiple()) {
            this.renderPreviewControl(guiGraphics, layout.leftArrow(), "<");
            this.renderPreviewControl(guiGraphics, layout.rightArrow(), ">");
        }
    }

    private PreviewLayout previewLayout() {
        if (this.previewOverlay == null || this.previewOverlay.current() == null) {
            return null;
        }

        CriterionSnapshot criterion = this.previewOverlay.current();
        Component title = criterion.displayName();
        Component subtitle = this.previewSubtitle(criterion);
        int textHeight = subtitle != null ? 24 : 14;
        int maxVisualWidth = Math.max(96, Math.round(this.width * PREVIEW_MAX_WIDTH_RATIO));
        int maxVisualHeight = Math.max(96, Math.round(this.height * PREVIEW_MAX_HEIGHT_RATIO));
        int textWidth = Math.max(
                this.font.width(title),
                subtitle != null ? this.font.width(subtitle) : 0
        );
        int visualWidth = maxVisualWidth;
        int visualHeight = maxVisualHeight;
        int panelWidth = Math.max(visualWidth, textWidth) + PREVIEW_PANEL_PADDING * 2;
        int panelHeight = visualHeight + PREVIEW_PANEL_PADDING * 2 + textHeight;
        int panelX = (this.width - panelWidth) / 2;
        int panelY = (this.height - panelHeight) / 2;
        int visualX = panelX + PREVIEW_PANEL_PADDING;
        int visualY = panelY + PREVIEW_PANEL_PADDING + textHeight;
        Rect closeButton = new Rect(
                panelX + panelWidth - PREVIEW_CLOSE_SIZE - 5,
                panelY + 5,
                PREVIEW_CLOSE_SIZE,
                PREVIEW_CLOSE_SIZE
        );
        Rect leftArrow = new Rect(
                visualX + 4,
                visualY + (visualHeight - PREVIEW_NAV_HEIGHT) / 2,
                PREVIEW_NAV_WIDTH,
                PREVIEW_NAV_HEIGHT
        );
        Rect rightArrow = new Rect(
                visualX + visualWidth - PREVIEW_NAV_WIDTH - 4,
                visualY + (visualHeight - PREVIEW_NAV_HEIGHT) / 2,
                PREVIEW_NAV_WIDTH,
                PREVIEW_NAV_HEIGHT
        );
        return new PreviewLayout(panelX, panelY, panelWidth, panelHeight, visualX, visualY, visualWidth, visualHeight, closeButton, leftArrow, rightArrow);
    }

    private void renderPreviewControl(GuiGraphicsExtractor guiGraphics, Rect rect, String label) {
        guiGraphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0xB02D1717);
        guiGraphics.outline(rect.x(), rect.y(), rect.width(), rect.height(), 0xFFF0D3A3);
        guiGraphics.centeredText(
                this.font,
                label,
                rect.x() + rect.width() / 2,
                rect.y() + rect.height() / 2 - 4,
                TEXT_HEADER
        );
    }

    private void navigatePreview(int direction) {
        if (this.previewOverlay == null || !this.previewOverlay.hasMultiple()) {
            return;
        }

        this.previewOverlay = this.previewOverlay.navigate(direction);
        this.syncPreviewVideoCache();
    }

    private void closePreviewOverlay() {
        if (this.previewOverlay == null) {
            return;
        }

        this.previewOverlay = null;
        this.biomeAnimationCache.releaseSheets();
    }

    private void syncPreviewVideoCache() {
        if (this.previewOverlay == null || this.previewOverlay.criteria().isEmpty()) {
            this.biomeAnimationCache.releaseSheets();
            return;
        }

        List<BiomePreviewSpec> retainedSpecs = new ArrayList<>(3);
        this.addBiomePreviewSpec(retainedSpecs, this.previewOverlay.selectedIndex());
        if (this.previewOverlay.hasMultiple()) {
            this.addBiomePreviewSpec(retainedSpecs, Math.floorMod(this.previewOverlay.selectedIndex() - 1, this.previewOverlay.criteria().size()));
            this.addBiomePreviewSpec(retainedSpecs, Math.floorMod(this.previewOverlay.selectedIndex() + 1, this.previewOverlay.criteria().size()));
        }
        this.biomeAnimationCache.retainSheets(retainedSpecs);
    }

    private void addBiomePreviewSpec(List<BiomePreviewSpec> specs, int index) {
        if (this.previewOverlay == null || specs == null || this.previewOverlay.criteria().isEmpty()) {
            return;
        }

        CriterionSnapshot criterion = this.previewOverlay.criteria().get(index);
        if (criterion == null || criterion.icon() == null || !criterion.icon().isBiomePreview()) {
            return;
        }

        BiomePreviewSpec spec = criterion.icon().biomePreviewSpec();
        if (spec == null || spec.biomeId() == null) {
            return;
        }

        for (BiomePreviewSpec existing : specs) {
            if (existing != null && spec.biomeId().equals(existing.biomeId())) {
                return;
            }
        }
        specs.add(spec);
    }

    private void renderRequirementScrollbar(
            GuiGraphicsExtractor guiGraphics,
            int x,
            int y,
            int width,
            int height,
            int contentHeight,
            int scrollOffset
    ) {
        guiGraphics.fill(x, y, x + width, y + height, 0x5D3A2323);
        guiGraphics.outline(x, y, width, height, 0x8C8E6565);

        int thumbHeight = Math.max(18, Math.round(height * (height / (float) contentHeight)));
        int thumbTravel = Math.max(0, height - thumbHeight);
        int maxScroll = Math.max(1, contentHeight - height);
        int thumbY = y + Math.round(thumbTravel * (scrollOffset / (float) maxScroll));
        guiGraphics.fill(x + 1, thumbY, x + width - 1, thumbY + thumbHeight, 0xC8C79D62);
        guiGraphics.outline(x, thumbY, width, thumbHeight, 0xFFF0D3A3);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String ellipsize(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }

        String trimmed = this.font.plainSubstrByWidth(text, Math.max(10, width - this.font.width("...")));
        return trimmed + "...";
    }

    private String singleLineText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        return text.replaceAll("\\s*\\R+\\s*", " ").replaceAll("\\s+", " ").trim();
    }

    private void refreshItemEntries(boolean force) {
        long now = System.currentTimeMillis();
        boolean itemsTabActive = ITEMS_TAB_ID.equals(this.activeRootId);
        if (!force && !itemsTabActive && !this.cachedItemEntries.isEmpty()) {
            return;
        }

        if (!force && now < this.nextItemScanAt && !this.cachedItemEntries.isEmpty()) {
            return;
        }

        this.cachedItemEntries = this.scanItemObjectives();
        this.nextItemScanAt = now + ITEM_SCAN_INTERVAL_MILLIS;
    }

    private List<ItemProgressSnapshot> scanItemObjectives() {
        ItemScanResult scanResult = new ItemScanResult();
        Minecraft minecraft = Minecraft.getInstance();
        this.scanAvailablePlayerStorage(minecraft, scanResult);

        List<ItemProgressSnapshot> items = new ArrayList<>(12);
        items.add(this.createEnderChestProgress(scanResult));
        items.add(this.createConduitProgress(scanResult));
        items.add(this.createBeaconProgress(scanResult));
        items.add(this.createNetheriteProgress(scanResult));
        items.add(this.createOminousBottlesProgress(scanResult));
        items.add(this.createEndCrystalProgress(scanResult));
        items.add(this.createSmithingWithStyleProgress(scanResult));
        items.add(this.createSingleItemProgress(
                Identifier.fromNamespaceAndPath("aaigttracker", "enchanted_golden_apple"),
                Component.translatable("screen.aaigttracker.items.ega.title"),
                Component.translatable("screen.aaigttracker.items.ega.description"),
                Items.ENCHANTED_GOLDEN_APPLE.getDefaultInstance(),
                scanResult,
                Items.ENCHANTED_GOLDEN_APPLE,
                1
        ));
        items.add(this.createSingleItemProgress(
                Identifier.fromNamespaceAndPath("aaigttracker", "sniffer_eggs"),
                Component.translatable("screen.aaigttracker.items.sniffer_eggs.title"),
                Component.translatable("screen.aaigttracker.items.sniffer_eggs.description"),
                Items.SNIFFER_EGG.getDefaultInstance(),
                scanResult,
                Items.SNIFFER_EGG,
                2
        ));
        items.add(this.createTridentProgress(scanResult));
        items.add(this.createPiercingCrossbowProgress(scanResult));
        items.add(this.createPotionProgress(scanResult));
        return List.copyOf(items);
    }

    private void scanAvailablePlayerStorage(Minecraft minecraft, ItemScanResult scanResult) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        if (this.scanServerPlayerStorage(minecraft, scanResult)) {
            return;
        }

        this.scanContainer(minecraft.player.getInventory(), scanResult);
        this.scanContainer(minecraft.player.getEnderChestInventory(), scanResult);
    }

    private boolean scanServerPlayerStorage(Minecraft minecraft, ItemScanResult scanResult) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return false;
        }

        UUID playerId = minecraft.player.getUUID();
        try {
            List<ItemStack> stacks = server.submit(() -> {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
                if (serverPlayer == null) {
                    return List.<ItemStack>of();
                }

                List<ItemStack> copiedStacks = new ArrayList<>();
                copyContainerStacks(serverPlayer.getInventory(), copiedStacks);
                copyContainerStacks(serverPlayer.getEnderChestInventory(), copiedStacks);
                return List.copyOf(copiedStacks);
            }).join();
            if (stacks.isEmpty()) {
                return false;
            }
            for (ItemStack stack : stacks) {
                this.scanItemStack(stack, scanResult, 0);
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void copyContainerStacks(Container container, List<ItemStack> stacks) {
        if (container == null) {
            return;
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
    }

    private ItemProgressSnapshot createEnderChestProgress(ItemScanResult scanResult) {
        int enderChestCount = scanResult.itemCount(Items.ENDER_CHEST);
        int obsidianCount = scanResult.itemCount(Items.OBSIDIAN);
        int eyeCount = scanResult.itemCount(Items.ENDER_EYE);
        boolean hasEnderChest = enderChestCount >= 1;
        boolean hasMaterials = obsidianCount >= 8 && eyeCount >= 1;
        boolean done = hasEnderChest || hasMaterials;
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("ender_chest", Items.ENDER_CHEST, enderChestCount, 1, hasEnderChest),
                this.countedItemCriterion("ender_chest_obsidian", Items.OBSIDIAN, obsidianCount, 8, done || obsidianCount >= 8),
                this.countedItemCriterion("ender_chest_eye", Items.ENDER_EYE, eyeCount, 1, done || eyeCount >= 1)
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "ender_chest"),
                Component.translatable("screen.aaigttracker.items.ender_chest.title"),
                Component.translatable("screen.aaigttracker.items.ender_chest.description"),
                Items.ENDER_CHEST.getDefaultInstance(),
                done ? 9 : Math.min(8, obsidianCount) + Math.min(1, eyeCount),
                9,
                done,
                criteria
        );
    }

    private ItemProgressSnapshot createOminousBottlesProgress(ItemScanResult scanResult) {
        int bottleCount = scanResult.itemCount(Items.OMINOUS_BOTTLE);
        int activeCredit = this.activeOminousBottleCredit();
        int bottlesNeeded = Math.max(0, 4 - activeCredit);
        CriterionSnapshot criterion = this.countedItemCriterion(
                "ominous_bottle",
                Items.OMINOUS_BOTTLE.getDefaultInstance(),
                Component.literal(Items.OMINOUS_BOTTLE.getName(Items.OMINOUS_BOTTLE.getDefaultInstance()).getString()
                        + " " + bottleCount + "/" + bottlesNeeded),
                bottleCount,
                bottlesNeeded
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "ominous_bottles"),
                Component.translatable("screen.aaigttracker.items.ominous_bottles.title"),
                Component.translatable("screen.aaigttracker.items.ominous_bottles.description"),
                Items.OMINOUS_BOTTLE.getDefaultInstance(),
                Math.min(4, bottleCount + activeCredit),
                4,
                bottleCount >= bottlesNeeded,
                List.of(criterion)
        );
    }

    private int activeOminousBottleCredit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }

        int credit = 0;
        if (minecraft.player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            credit++;
        }
        if (minecraft.player.hasEffect(MobEffects.TRIAL_OMEN)) {
            credit++;
        }
        return credit;
    }

    private ItemProgressSnapshot createTridentProgress(ItemScanResult scanResult) {
        int tridentCount = scanResult.itemCount(Items.TRIDENT);
        int channelingTridentCount = scanResult.channelingTridentCount();
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("trident", Items.TRIDENT, tridentCount, 1),
                this.countedItemCriterion(
                        "channeling_trident",
                        Items.ENCHANTED_BOOK.getDefaultInstance(),
                        Component.literal("Channeling Trident " + channelingTridentCount + "/1"),
                        channelingTridentCount,
                        1
                )
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "trident"),
                Component.translatable("screen.aaigttracker.items.trident.title"),
                Component.translatable("screen.aaigttracker.items.trident.description"),
                Items.TRIDENT.getDefaultInstance(),
                Math.min(1, tridentCount) + Math.min(1, channelingTridentCount),
                2,
                tridentCount >= 1 && channelingTridentCount >= 1,
                criteria
        );
    }

    private ItemProgressSnapshot createPiercingCrossbowProgress(ItemScanResult scanResult) {
        int crossbowCount = scanResult.itemCount(Items.CROSSBOW);
        int piercingCrossbowCount = scanResult.piercingCrossbowCount();
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("crossbow", Items.CROSSBOW, crossbowCount, 1),
                this.countedItemCriterion(
                        "piercing_crossbow",
                        Items.ENCHANTED_BOOK.getDefaultInstance(),
                        Component.literal("Piercing IV Crossbow " + piercingCrossbowCount + "/1"),
                        piercingCrossbowCount,
                        1
                )
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "piercing_crossbow"),
                Component.translatable("screen.aaigttracker.items.piercing_crossbow.title"),
                Component.translatable("screen.aaigttracker.items.piercing_crossbow.description"),
                Items.CROSSBOW.getDefaultInstance(),
                Math.min(1, crossbowCount) + Math.min(1, piercingCrossbowCount),
                2,
                crossbowCount >= 1 && piercingCrossbowCount >= 1,
                criteria
        );
    }

    private ItemProgressSnapshot createEndCrystalProgress(ItemScanResult scanResult) {
        int crystalCount = scanResult.itemCount(Items.END_CRYSTAL);
        int remainingCrystals = Math.max(0, 4 - crystalCount);
        int glassTarget = remainingCrystals * 7;
        int eyeTarget = remainingCrystals;
        int tearTarget = remainingCrystals;
        int glassCount = scanResult.itemCount(Items.GLASS);
        int eyeCount = scanResult.itemCount(Items.ENDER_EYE);
        int tearCount = scanResult.itemCount(Items.GHAST_TEAR);
        int craftableCrystals = remainingCrystals == 0
                ? 0
                : Math.min(glassCount / 7, Math.min(eyeCount, tearCount));
        int availableCrystals = Math.min(4, crystalCount + craftableCrystals);
        boolean done = availableCrystals >= 4;
        List<CriterionSnapshot> criteria = new ArrayList<>(4);
        criteria.add(this.countedItemCriterion("end_crystal", Items.END_CRYSTAL, crystalCount, 4, crystalCount >= 4));
        if (remainingCrystals > 0) {
            criteria.add(this.countedItemCriterion("end_crystal_glass", Items.GLASS, glassCount, glassTarget, done || glassCount >= glassTarget));
            criteria.add(this.countedItemCriterion("end_crystal_eye", Items.ENDER_EYE, eyeCount, eyeTarget, done || eyeCount >= eyeTarget));
            criteria.add(this.countedItemCriterion("end_crystal_tear", Items.GHAST_TEAR, tearCount, tearTarget, done || tearCount >= tearTarget));
        }
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "end_crystals"),
                Component.translatable("screen.aaigttracker.items.end_crystals.title"),
                Component.translatable("screen.aaigttracker.items.end_crystals.description"),
                Items.END_CRYSTAL.getDefaultInstance(),
                availableCrystals,
                4,
                done,
                List.copyOf(criteria)
        );
    }

    private ItemProgressSnapshot createSmithingWithStyleProgress(ItemScanResult scanResult) {
        List<CriterionSnapshot> criteria = new ArrayList<>(EXCLUSIVE_TRIM_TEMPLATES.size());
        int completed = 0;
        for (TrimTemplateObjective objective : EXCLUSIVE_TRIM_TEMPLATES) {
            int count = scanResult.itemCount(objective.item());
            String criterionKey = trimCriterionKey(objective.pattern());
            boolean crafted = this.isAdvancementCriterionComplete(SMITHING_WITH_STYLE_ADVANCEMENT_ID, criterionKey);
            boolean done = crafted || count >= 1;
            if (done) {
                completed++;
            }

            ItemStack stack = objective.item().getDefaultInstance();
            Component label = Component.literal(objective.item().getName(stack).getString()
                    + (crafted ? " done" : " " + count + "/1"));
            criteria.add(this.countedItemCriterion(criterionKey, stack, label, done));
        }

        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "smithing_with_style"),
                Component.translatable("screen.aaigttracker.items.smithing_with_style.title"),
                Component.translatable("screen.aaigttracker.items.smithing_with_style.description"),
                Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE.getDefaultInstance(),
                completed,
                EXCLUSIVE_TRIM_TEMPLATES.size(),
                completed >= EXCLUSIVE_TRIM_TEMPLATES.size(),
                List.copyOf(criteria)
        );
    }

    private void scanContainer(Container container, ItemScanResult scanResult) {
        if (container == null) {
            return;
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            this.scanItemStack(container.getItem(slot), scanResult, 0);
        }
    }

    private void scanItemStack(ItemStack stack, ItemScanResult scanResult, int depth) {
        if (stack == null || stack.isEmpty() || depth > ITEM_SCAN_DEPTH_LIMIT) {
            return;
        }

        scanResult.addItem(stack.getItem(), stack.getCount());
        if (stack.getItem() == Items.TRIDENT && this.hasEnchantmentLevel(stack, Enchantments.CHANNELING, 1)) {
            scanResult.addChannelingTridents(stack.getCount());
        }
        if (stack.getItem() == Items.CROSSBOW && this.hasEnchantmentLevel(stack, Enchantments.PIERCING, 4)) {
            scanResult.addPiercingCrossbows(stack.getCount());
        }

        if (stack.getItem() == Items.POTION
                || stack.getItem() == Items.SPLASH_POTION
                || stack.getItem() == Items.LINGERING_POTION) {
            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            if (potionContents != null) {
                potionContents.potion().ifPresent(holder -> scanResult.addPotion(
                        BuiltInRegistries.POTION.getKey(holder.value()),
                        stack.getCount()
                ));
            }
        }

        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents != null) {
            bundleContents.itemCopyStream().forEach(item -> this.scanItemStack(item, scanResult, depth + 1));
        }

        ItemContainerContents containerContents = stack.get(DataComponents.CONTAINER);
        if (containerContents != null) {
            containerContents.nonEmptyItemCopyStream().forEach(item -> this.scanItemStack(item, scanResult, depth + 1));
        }
    }

    private boolean hasEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantmentKey, int minimumLevel) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return false;
        }

        Holder.Reference<Enchantment> enchantment = minecraft.level
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(enchantmentKey)
                .orElse(null);
        return enchantment != null && enchantments.getLevel(enchantment) >= minimumLevel;
    }

    private ItemProgressSnapshot createConduitProgress(ItemScanResult scanResult) {
        int heartCount = scanResult.itemCount(Items.HEART_OF_THE_SEA);
        int shellCount = scanResult.itemCount(Items.NAUTILUS_SHELL);
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("heart_of_the_sea", Items.HEART_OF_THE_SEA, heartCount, 1),
                this.countedItemCriterion("nautilus_shell", Items.NAUTILUS_SHELL, shellCount, 8)
        );
        int current = Math.min(1, heartCount) + Math.min(8, shellCount);
        int total = 9;
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "conduit"),
                Component.translatable("screen.aaigttracker.items.conduit.title"),
                Component.translatable("screen.aaigttracker.items.conduit.description"),
                Items.CONDUIT.getDefaultInstance(),
                current,
                total,
                heartCount >= 1 && shellCount >= 8,
                criteria
        );
    }

    private ItemProgressSnapshot createBeaconProgress(ItemScanResult scanResult) {
        int skullCount = scanResult.itemCount(Items.WITHER_SKELETON_SKULL);
        int goldBlockCount = scanResult.itemCount(Items.GOLD_BLOCK);
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("wither_skeleton_skull", Items.WITHER_SKELETON_SKULL, skullCount, 3),
                this.countedItemCriterion("gold_block", Items.GOLD_BLOCK, goldBlockCount, 164)
        );
        int current = Math.min(3, skullCount) + Math.min(164, goldBlockCount);
        int total = 167;
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "beacon"),
                Component.translatable("screen.aaigttracker.items.beacon.title"),
                Component.translatable("screen.aaigttracker.items.beacon.description"),
                Items.BEACON.getDefaultInstance(),
                current,
                total,
                skullCount >= 3 && goldBlockCount >= 164,
                criteria
        );
    }

    private ItemProgressSnapshot createNetheriteProgress(ItemScanResult scanResult) {
        int debrisCount = scanResult.itemCount(Items.ANCIENT_DEBRIS);
        int scrapCount = scanResult.itemCount(Items.NETHERITE_SCRAP);
        int ingotCount = scanResult.itemCount(Items.NETHERITE_INGOT);
        int debrisEquivalent = debrisCount + scrapCount + ingotCount * 4;
        List<CriterionSnapshot> criteria = List.of(
                this.infoCriterion(
                        "ancient_debris",
                        Items.ANCIENT_DEBRIS.getDefaultInstance(),
                        Component.literal(Items.ANCIENT_DEBRIS.getName(Items.ANCIENT_DEBRIS.getDefaultInstance()).getString() + ": " + debrisCount)
                ),
                this.infoCriterion(
                        "netherite_scrap",
                        Items.NETHERITE_SCRAP.getDefaultInstance(),
                        Component.literal(Items.NETHERITE_SCRAP.getName(Items.NETHERITE_SCRAP.getDefaultInstance()).getString() + ": " + scrapCount)
                ),
                this.infoCriterion(
                        "netherite_ingot",
                        Items.NETHERITE_INGOT.getDefaultInstance(),
                        Component.literal(Items.NETHERITE_INGOT.getName(Items.NETHERITE_INGOT.getDefaultInstance()).getString() + ": " + ingotCount + " (" + (ingotCount * 4) + ")")
                )
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "netherite"),
                Component.translatable("screen.aaigttracker.items.netherite.title"),
                Component.translatable("screen.aaigttracker.items.netherite.description"),
                Items.NETHERITE_INGOT.getDefaultInstance(),
                Math.min(20, debrisEquivalent),
                20,
                debrisEquivalent >= 20,
                criteria
        );
    }

    private ItemProgressSnapshot createSingleItemProgress(
            Identifier id,
            Component title,
            Component description,
            ItemStack icon,
            ItemScanResult scanResult,
            Item targetItem,
            int targetCount
    ) {
        int count = scanResult.itemCount(targetItem);
        CriterionSnapshot criterion = this.countedItemCriterion(stripNamespace(id.toString()), targetItem, count, targetCount);
        return new ItemProgressSnapshot(
                id,
                title,
                description,
                icon,
                Math.min(targetCount, count),
                targetCount,
                count >= targetCount,
                List.of(criterion)
        );
    }

    private ItemProgressSnapshot createPotionProgress(ItemScanResult scanResult) {
        List<PotionObjectiveRow> rows = List.of(
                potionRow("swiftness", Potions.SWIFTNESS, Items.NETHER_WART, Items.SUGAR),
                potionRow("slowness", Potions.SLOWNESS, Items.NETHER_WART, Items.SUGAR, Items.FERMENTED_SPIDER_EYE),
                potionRow("night_vision", Potions.NIGHT_VISION, Items.NETHER_WART, Items.GOLDEN_CARROT),
                potionRow("invisibility", Potions.INVISIBILITY, Items.NETHER_WART, Items.GOLDEN_CARROT, Items.FERMENTED_SPIDER_EYE),
                potionRow("weakness", Potions.WEAKNESS, Items.FERMENTED_SPIDER_EYE),
                potionRow("strength", Potions.STRENGTH, Items.NETHER_WART, Items.BLAZE_POWDER),
                potionRow("slow_falling", Potions.SLOW_FALLING, Items.NETHER_WART, Items.PHANTOM_MEMBRANE),
                potionRow("water_breathing", Potions.WATER_BREATHING, Items.NETHER_WART, Items.PUFFERFISH),
                potionRow("oozing", Potions.OOZING, Items.NETHER_WART, Items.SLIME_BLOCK),
                potionRow("wind_charged", Potions.WIND_CHARGED, Items.NETHER_WART, Items.BREEZE_ROD),
                potionRow("weaving", Potions.WEAVING, Items.NETHER_WART, Items.COBWEB),
                potionRow("infested", Potions.INFESTED, Items.NETHER_WART, Items.STONE)
        );

        List<CriterionSnapshot> criteria = new ArrayList<>(rows.size());
        int completed = 0;
        for (PotionObjectiveRow row : rows) {
            int count = scanResult.potionCount(BuiltInRegistries.POTION.getKey(row.potion().value()));
            boolean done = count > 0;
            if (done) {
                completed++;
            }
            ItemStack icon = PotionContents.createItemStack(Items.POTION, row.potion());
            Component label = icon.getHoverName();
            List<CriterionIcon> recipeIcons = new ArrayList<>(row.ingredients().size());
            for (Item ingredient : row.ingredients()) {
                recipeIcons.add(CriterionIcon.item(ingredient.getDefaultInstance()));
            }
            criteria.add(new CriterionSnapshot(
                    row.key(),
                    label,
                    CriterionIcon.item(icon),
                    recipeIcons,
                    SupplementaryIconMode.STATIC_ALL,
                    done,
                    null,
                    false
            ));
        }

        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "potions"),
                Component.translatable("screen.aaigttracker.items.potions.title"),
                Component.translatable("screen.aaigttracker.items.potions.description"),
                Items.POTION.getDefaultInstance(),
                completed,
                rows.size(),
                completed >= rows.size(),
                List.copyOf(criteria)
        );
    }

    private CriterionSnapshot countedItemCriterion(String key, Item item, int current, int target) {
        return this.countedItemCriterion(key, item, current, target, current >= target);
    }

    private CriterionSnapshot countedItemCriterion(String key, Item item, int current, int target, boolean completed) {
        ItemStack stack = item.getDefaultInstance();
        Component label = Component.literal(item.getName(stack).getString() + " " + current + "/" + target);
        return this.countedItemCriterion(key, stack, label, completed);
    }

    private CriterionSnapshot countedItemCriterion(String key, ItemStack stack, Component label, int current, int target) {
        return this.countedItemCriterion(key, stack, label, current >= target);
    }

    private CriterionSnapshot countedItemCriterion(String key, ItemStack stack, Component label, boolean completed) {
        return new CriterionSnapshot(
                key,
                label,
                CriterionIcon.item(stack),
                List.of(),
                SupplementaryIconMode.NONE,
                completed,
                null,
                false
        );
    }

    private CriterionSnapshot infoCriterion(String key, ItemStack icon, Component label) {
        return new CriterionSnapshot(
                key,
                label,
                CriterionIcon.item(icon),
                List.of(),
                SupplementaryIconMode.NONE,
                false,
                null,
                false
        );
    }

    private PotionObjectiveRow potionRow(String key, net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion, Item... ingredients) {
        return new PotionObjectiveRow(key, potion, List.of(ingredients));
    }

    private void renderBiomePreview(
            GuiGraphicsExtractor guiGraphics,
            BiomePreviewSpec previewSpec,
            int x,
            int y,
            int width,
            int height,
            boolean modal
    ) {
        if (previewSpec == null || previewSpec.isEmpty()) {
            return;
        }

        if (modal) {
            this.biomeAnimationCache.requestSheet(previewSpec);
            BiomeAnimationCache.CachedSheet animationSheet = this.biomeAnimationCache.getSheet(previewSpec);
            if (animationSheet != null) {
                guiGraphics.enableScissor(x, y, x + width, y + height);
                this.renderBiomeAnimationSheet(guiGraphics, animationSheet, x, y, width, height);
                guiGraphics.disableScissor();
                return;
            }
        }

        BiomeAnimationCache.CachedFrame previewFrame = this.biomeAnimationCache.getPreviewFrame(previewSpec);
        if (previewFrame != null) {
            guiGraphics.enableScissor(x, y, x + width, y + height);
            this.renderBiomeAnimationFrame(guiGraphics, previewFrame, x, y, width, height);
            guiGraphics.disableScissor();
        }
    }

    private void renderBiomeAnimationSheet(
            GuiGraphicsExtractor guiGraphics,
            BiomeAnimationCache.CachedSheet sheet,
            int x,
            int y,
            int width,
            int height
    ) {
        int frameIndex = sheet.frameIndex(this.currentRenderMillis);
        int sourceX = (frameIndex % sheet.columns()) * sheet.frameWidth();
        int sourceY = (frameIndex / sheet.columns()) * sheet.frameHeight();
        Rect drawRect = containRect(x, y, width, height, sheet.frameWidth(), sheet.frameHeight());
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                sheet.textureId(),
                drawRect.x(),
                drawRect.y(),
                (float) sourceX,
                (float) sourceY,
                drawRect.width(),
                drawRect.height(),
                sheet.frameWidth(),
                sheet.frameHeight(),
                sheet.sheetWidth(),
                sheet.sheetHeight()
        );
    }

    private void renderBiomeAnimationFrame(
            GuiGraphicsExtractor guiGraphics,
            BiomeAnimationCache.CachedFrame frame,
            int x,
            int y,
            int width,
            int height
    ) {
        Rect drawRect = containRect(x, y, width, height, frame.width(), frame.height());
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                frame.textureId(),
                drawRect.x(),
                drawRect.y(),
                0.0F,
                0.0F,
                drawRect.width(),
                drawRect.height(),
                frame.width(),
                frame.height(),
                frame.width(),
                frame.height()
        );
    }

    private static Rect containRect(int x, int y, int width, int height, int sourceWidth, int sourceHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || width <= 0 || height <= 0) {
            return new Rect(x, y, Math.max(0, width), Math.max(0, height));
        }

        float sourceAspect = sourceWidth / (float) sourceHeight;
        float targetAspect = width / (float) height;
        int drawWidth = width;
        int drawHeight = height;
        int drawX = x;
        int drawY = y;
        if (targetAspect > sourceAspect) {
            drawHeight = height;
            drawWidth = Math.max(1, Math.round(drawHeight * sourceAspect));
            drawX = x + (width - drawWidth) / 2;
        } else {
            drawWidth = width;
            drawHeight = Math.max(1, Math.round(drawWidth / sourceAspect));
            drawY = y + (height - drawHeight) / 2;
        }
        return new Rect(drawX, drawY, drawWidth, drawHeight);
    }

    private void renderEntityPortrait(
            GuiGraphicsExtractor guiGraphics,
            LivingEntity entity,
            int x,
            int y,
            int width,
            int height,
            boolean rotating
    ) {
        float yaw = rotating
                ? (this.currentRenderMillis % PREVIEW_ROTATION_PERIOD_MILLIS) * 360.0F / PREVIEW_ROTATION_PERIOD_MILLIS
                : 0.0F;
        float ageInTicks = rotating ? (this.currentRenderMillis % 120_000L) / 50.0F : 0.0F;
        entity.setPos(0, 0, 0);
        entity.tickCount = (int) ageInTicks;
        this.stabilizePreviewEntity(entity);
        this.applyPreviewRotation(entity);

        EntityRenderState renderState = this.extractPreviewRenderState(entity, yaw, ageInTicks, rotating);
        if (renderState == null) {
            return;
        }

        int renderX = x;
        int renderY = y;
        int renderWidth = width;
        int renderHeight = height;
        if (!rotating) {
            renderX = x + 2;
            renderY = y + 2;
            renderWidth = Math.max(1, width - 4);
            renderHeight = Math.max(1, height - 4);
            guiGraphics.enableScissor(x, y, x + width, y + height);
        }

        float size = rotating
                ? this.modalEntityRenderSize(renderState, renderWidth, renderHeight)
                : this.rowEntityRenderSize(renderState, renderWidth, renderHeight);
        Vector3f offset = new Vector3f(0.0F, this.previewVerticalOffset(renderState, rotating), 0.0F);
        Quaternionf baseRotation = new Quaternionf()
                .rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(PREVIEW_CAMERA_X_ROTATION_DEGREES));
        if (renderState.entityType == EntityType.ENDER_DRAGON && rotating) {
            baseRotation.rotateY((float) Math.toRadians(yaw));
        }
        Quaternionf cameraTilt = new Quaternionf().rotateX((float) Math.toRadians(PREVIEW_CAMERA_X_ROTATION_DEGREES));

        guiGraphics.entity(
                renderState,
                size,
                offset,
                baseRotation,
                cameraTilt,
                renderX,
                renderY,
                renderX + renderWidth,
                renderY + renderHeight
        );
        if (!rotating) {
            guiGraphics.disableScissor();
        }
    }

    private void stabilizePreviewEntity(LivingEntity entity) {
        if (entity instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
            hoglin.setTimeInOverworld(0);
        }
        if (entity instanceof AbstractPiglin piglin) {
            piglin.setImmuneToZombification(true);
            piglin.setTimeInOverworld(0);
        }
    }

    private void applyPreviewRotation(LivingEntity entity) {
        entity.setYRot(0.0F);
        entity.yRotO = 0.0F;
        entity.setYBodyRot(0.0F);
        entity.yBodyRot = 0.0F;
        entity.yBodyRotO = 0.0F;
        entity.setYHeadRot(0.0F);
        entity.yHeadRot = 0.0F;
        entity.yHeadRotO = 0.0F;
        entity.setXRot(0.0F);
        entity.xRotO = 0.0F;
    }

    private EntityRenderState extractPreviewRenderState(LivingEntity entity, float yaw, float ageInTicks, boolean animated) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return null;
        }

        EntityRenderState renderState = minecraft.getEntityRenderDispatcher().extractEntity(entity, 1.0F);
        renderState.ageInTicks = ageInTicks;
        renderState.shadowPieces.clear();

        if (renderState instanceof LivingEntityRenderState livingState) {
            float scale = livingState.scale == 0.0F ? 1.0F : livingState.scale;
            livingState.boundingBoxWidth /= scale;
            livingState.boundingBoxHeight /= scale;
            livingState.scale = 1.0F;
            livingState.bodyRot = 180.0F + yaw;
            livingState.yRot = 0.0F;
            livingState.xRot = 0.0F;
            livingState.walkAnimationPos = animated ? ageInTicks * 0.08F : 0.0F;
            livingState.walkAnimationSpeed = animated ? 0.08F : 0.0F;
        }

        if (renderState instanceof EnderDragonRenderState dragonState) {
            dragonState.flapTime = animated ? ageInTicks * 0.045F : 0.0F;
            dragonState.partialTicks = animated ? 1.0F : 0.0F;
        }

        renderState.boundingBoxWidth = Math.max(0.1F, renderState.boundingBoxWidth);
        renderState.boundingBoxHeight = Math.max(0.1F, renderState.boundingBoxHeight);
        return renderState;
    }

    private float rowEntityRenderSize(EntityRenderState renderState, int width, int height) {
        float widthFit = width * 0.86F / this.entityFitWidth(renderState);
        float heightFit = height * 0.90F / renderState.boundingBoxHeight;
        return Mth.clamp(Math.min(widthFit, heightFit), 2.0F, 22.0F);
    }

    private float modalEntityRenderSize(EntityRenderState renderState, int width, int height) {
        float widthRatio = renderState.entityType == EntityType.ENDER_DRAGON ? 0.82F : 0.56F;
        float heightRatio = renderState.entityType == EntityType.ENDER_DRAGON ? 0.72F : 0.76F;
        float maxSize = renderState.entityType == EntityType.ENDER_DRAGON ? 120.0F : 210.0F;
        float widthFit = width * widthRatio / this.entityFitWidth(renderState);
        float heightFit = height * heightRatio / renderState.boundingBoxHeight;
        return Mth.clamp(Math.min(widthFit, heightFit), 18.0F, maxSize);
    }

    private float previewVerticalOffset(EntityRenderState renderState, boolean rotating) {
        if (renderState.entityType == EntityType.ENDER_DRAGON) {
            return renderState.boundingBoxHeight * (rotating ? 0.50F : 0.60F);
        }
        if (!rotating) {
            return renderState.boundingBoxHeight * 0.62F;
        }
        return renderState.boundingBoxHeight / 2.0F;
    }

    private float entityFitWidth(EntityRenderState renderState) {
        float width = Math.max(0.1F, renderState.boundingBoxWidth);
        if (renderState.entityType == EntityType.ENDER_DRAGON) {
            return width * 1.85F;
        }
        if (renderState.entityType == EntityType.GHAST || renderState.entityType == EntityType.PHANTOM) {
            return width * 1.35F;
        }
        return width;
    }

    private LivingEntity previewEntity(EntityPreviewSpec previewSpec) {
        if (previewSpec == null) {
            return null;
        }

        LivingEntity cached = this.previewEntities.get(previewSpec);
        if (cached != null) {
            return cached;
        }

        LivingEntity created = this.createPreviewEntity(previewSpec);
        if (created != null) {
            this.previewEntities.put(previewSpec, created);
        }
        return created;
    }

    private LivingEntity createPreviewEntity(EntityPreviewSpec previewSpec) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return null;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(previewSpec.entityId()).orElse(null);
        if (entityType == null) {
            return null;
        }

        Entity entity = entityType.create(minecraft.level, EntitySpawnReason.COMMAND);
        if (!(entity instanceof LivingEntity livingEntity)) {
            return null;
        }

        this.applyPreviewVariant(livingEntity, previewSpec);
        this.stabilizePreviewEntity(livingEntity);
        livingEntity.setYRot(0.0F);
        livingEntity.setXRot(0.0F);
        livingEntity.setYHeadRot(0.0F);
        livingEntity.setYBodyRot(0.0F);
        return livingEntity;
    }

    private void applyPreviewVariant(LivingEntity entity, EntityPreviewSpec previewSpec) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || previewSpec.variantId() == null) {
            return;
        }

        switch (previewSpec.variantType()) {
            case CAT -> {
                var registry = minecraft.level.registryAccess().lookupOrThrow(Registries.CAT_VARIANT);
                var variant = registry.get(previewSpec.variantId()).orElse(null);
                if (variant != null) {
                    entity.setComponent(DataComponents.CAT_VARIANT, variant);
                }
            }
            case WOLF -> {
                var registry = minecraft.level.registryAccess().lookupOrThrow(Registries.WOLF_VARIANT);
                var variant = registry.get(previewSpec.variantId()).orElse(null);
                if (variant != null) {
                    entity.setComponent(DataComponents.WOLF_VARIANT, variant);
                }
            }
            case FROG -> {
                var registry = minecraft.level.registryAccess().lookupOrThrow(Registries.FROG_VARIANT);
                var variant = registry.get(previewSpec.variantId()).orElse(null);
                if (variant != null) {
                    entity.setComponent(DataComponents.FROG_VARIANT, variant);
                }
            }
            case NONE -> {
            }
        }
    }

    private boolean isAdvancementCriterionComplete(Identifier advancementId, String criterionKey) {
        if (advancementId == null || criterionKey == null) {
            return false;
        }

        for (AdvancementSnapshot snapshot : this.cachedEntries) {
            if (!snapshot.id().equals(advancementId)) {
                continue;
            }
            for (CriterionSnapshot criterion : snapshot.criteria()) {
                String key = criterion.key();
                if ((criterionKey.equals(key) || criterionKey.equals(stripNamespace(key))) && criterion.completed()) {
                    return true;
                }
            }
            return snapshot.done();
        }
        return false;
    }

    private static String trimCriterionKey(String pattern) {
        return "armor_trimmed_minecraft:" + pattern + "_armor_trim_smithing_template_smithing_trim";
    }

    private static String trimPatternKey(String criterionKey) {
        if (criterionKey == null || !criterionKey.startsWith("armor_trimmed_")) {
            return null;
        }

        int namespaceIndex = criterionKey.indexOf(':');
        String key = namespaceIndex >= 0 && namespaceIndex + 1 < criterionKey.length()
                ? criterionKey.substring(namespaceIndex + 1)
                : criterionKey.substring("armor_trimmed_".length());
        String suffix = "_armor_trim_smithing_template_smithing_trim";
        return key.endsWith(suffix) ? key.substring(0, key.length() - suffix.length()) : null;
    }

    private static String stripNamespace(String value) {
        if (value == null) {
            return "";
        }

        int separatorIndex = value.indexOf(':');
        return separatorIndex >= 0 && separatorIndex + 1 < value.length()
                ? value.substring(separatorIndex + 1)
                : value;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static String formatMillis(Long millis) {
        if (millis == null) {
            return "--:--:--.--";
        }

        long hours = millis / 3_600_000L;
        long minutes = (millis / 60_000L) % 60L;
        long seconds = (millis / 1_000L) % 60L;
        long centiseconds = (millis / 10L) % 100L;
        return String.format("%02d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds);
    }

    private sealed interface BoardEntry permits AdvancementBoardEntry, ItemBoardEntry {
        Identifier id();
        Component title();
        ItemStack icon();
        boolean done();
    }

    private record AdvancementBoardEntry(AdvancementSnapshot snapshot) implements BoardEntry {
        @Override
        public Identifier id() {
            return this.snapshot.id();
        }

        @Override
        public Component title() {
            return this.snapshot.title();
        }

        @Override
        public ItemStack icon() {
            return this.snapshot.icon().copy();
        }

        @Override
        public boolean done() {
            return this.snapshot.done();
        }
    }

    private record ItemBoardEntry(ItemProgressSnapshot snapshot) implements BoardEntry {
        @Override
        public Identifier id() {
            return this.snapshot.id();
        }

        @Override
        public Component title() {
            return this.snapshot.title();
        }

        @Override
        public ItemStack icon() {
            return this.snapshot.icon().copy();
        }

        @Override
        public boolean done() {
            return this.snapshot.done();
        }
    }

    private enum TabKind {
        ADVANCEMENTS,
        ITEMS
    }

    private record BoardTab(
            Identifier rootId,
            Component title,
            ItemStack icon,
            List<BoardEntry> entries,
            TabKind kind
    ) {
    }

    private record TabButton(int index, int x, int y, int width, int height) {
    }

    private record TileHitbox(BoardEntry entry, int x, int y, int width, int height) {
    }

    private record RequirementHitbox(
            CriterionSnapshot criterion,
            int x,
            int y,
            int width,
            int height,
            int primaryIconX,
            int primaryIconY,
            int primaryIconWidth,
            int primaryIconHeight
    ) {
        private boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }

        private boolean containsPrimaryIcon(double mouseX, double mouseY) {
            return this.primaryIconWidth > 0
                    && this.primaryIconHeight > 0
                    && inside(mouseX, mouseY, this.primaryIconX, this.primaryIconY, this.primaryIconWidth, this.primaryIconHeight);
        }
    }

    private record RequirementViewport(int x, int y, int width, int height, int maxScroll) {
        private boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }

    private record HoveredTile(BoardEntry entry) {
    }

    private record GridLayout(
            int columns,
            int rows,
            int tileWidth,
            int tileHeight,
            float iconScale,
            int iconSize
    ) {
        private int totalWidth() {
            return this.columns * this.tileWidth + Math.max(0, this.columns - 1) * TILE_GAP;
        }

        private int totalHeight() {
            return this.rows * this.tileHeight + Math.max(0, this.rows - 1) * TILE_GAP;
        }
    }

    private enum RequirementMode {
        SINGLE,
        ONE_OF,
        ALL_OF,
        GROUPED
    }

    private record RequirementSection(Component label, List<CriterionSnapshot> criteria) {
    }

    private record RequirementSectionLayout(
            RequirementSection section,
            int columns,
            int rows,
            int columnWidth,
            int height
    ) {
    }

    private record RequirementRenderData(
            List<RequirementSectionLayout> layouts,
            int contentWidth,
            int contentHeight,
            int overflowHeight
    ) {
    }

    private record PreviewOverlay(List<CriterionSnapshot> criteria, int selectedIndex) {
        private PreviewOverlay {
            criteria = criteria == null ? List.of() : List.copyOf(criteria);
            if (criteria.isEmpty()) {
                selectedIndex = 0;
            } else {
                selectedIndex = clamp(selectedIndex, 0, criteria.size() - 1);
            }
        }

        private CriterionSnapshot current() {
            return this.criteria.isEmpty() ? null : this.criteria.get(this.selectedIndex);
        }

        private boolean hasMultiple() {
            return this.criteria.size() > 1;
        }

        private PreviewOverlay navigate(int direction) {
            if (this.criteria.isEmpty()) {
                return this;
            }

            int nextIndex = Math.floorMod(this.selectedIndex + direction, this.criteria.size());
            return new PreviewOverlay(this.criteria, nextIndex);
        }
    }

    private record PreviewLayout(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            int visualX,
            int visualY,
            int visualWidth,
            int visualHeight,
            Rect closeButton,
            Rect leftArrow,
            Rect rightArrow
    ) {
        private int panelRight() {
            return this.panelX + this.panelWidth;
        }

        private int panelBottom() {
            return this.panelY + this.panelHeight;
        }
    }

    private record Rect(int x, int y, int width, int height) {
        private int right() {
            return this.x + this.width;
        }

        private int bottom() {
            return this.y + this.height;
        }

        private boolean contains(double mouseX, double mouseY) {
            return inside(mouseX, mouseY, this.x, this.y, this.width, this.height);
        }
    }

    private record ItemCompletionGate(List<GateRequirement> requirements) {
        private static ItemCompletionGate advancement(Identifier advancementId) {
            return new ItemCompletionGate(List.of(new GateRequirement(advancementId, null)));
        }

        private static ItemCompletionGate advancements(Identifier... advancementIds) {
            List<GateRequirement> requirements = new ArrayList<>(advancementIds.length);
            for (Identifier advancementId : advancementIds) {
                requirements.add(new GateRequirement(advancementId, null));
            }
            return new ItemCompletionGate(List.copyOf(requirements));
        }

        private static ItemCompletionGate criterion(Identifier advancementId, String criterionKey) {
            return new ItemCompletionGate(List.of(new GateRequirement(advancementId, criterionKey)));
        }

        private boolean isComplete(List<AdvancementSnapshot> snapshots) {
            for (GateRequirement requirement : this.requirements) {
                if (!requirement.isComplete(snapshots)) {
                    return false;
                }
            }
            return !this.requirements.isEmpty();
        }
    }

    private record GateRequirement(Identifier advancementId, String criterionKey) {
        private boolean isComplete(List<AdvancementSnapshot> snapshots) {
            for (AdvancementSnapshot snapshot : snapshots) {
                if (snapshot.id().equals(this.advancementId)) {
                    return this.isSnapshotComplete(snapshot);
                }
            }
            return false;
        }

        private boolean isSnapshotComplete(AdvancementSnapshot snapshot) {
            if (this.criterionKey == null) {
                return snapshot.done();
            }

            for (CriterionSnapshot criterion : snapshot.criteria()) {
                String key = criterion.key();
                if ((this.criterionKey.equals(key) || this.criterionKey.equals(stripNamespace(key))) && criterion.completed()) {
                    return true;
                }
            }
            return snapshot.done();
        }
    }

    private static final class ItemScanResult {
        private final Map<Item, Integer> itemCounts;
        private final Map<Identifier, Integer> potionCounts;
        private int channelingTridentCount;
        private int piercingCrossbowCount;

        private ItemScanResult() {
            this.itemCounts = new HashMap<>();
            this.potionCounts = new HashMap<>();
        }

        private void addItem(Item item, int amount) {
            if (item == null || amount <= 0) {
                return;
            }
            this.itemCounts.merge(item, amount, Integer::sum);
        }

        private void addPotion(Identifier potionId, int amount) {
            if (potionId == null || amount <= 0) {
                return;
            }
            this.potionCounts.merge(potionId, amount, Integer::sum);
        }

        private void addChannelingTridents(int amount) {
            if (amount > 0) {
                this.channelingTridentCount += amount;
            }
        }

        private void addPiercingCrossbows(int amount) {
            if (amount > 0) {
                this.piercingCrossbowCount += amount;
            }
        }

        private int itemCount(Item item) {
            return this.itemCounts.getOrDefault(item, 0);
        }

        private int potionCount(Identifier potionId) {
            return this.potionCounts.getOrDefault(potionId, 0);
        }

        private int channelingTridentCount() {
            return this.channelingTridentCount;
        }

        private int piercingCrossbowCount() {
            return this.piercingCrossbowCount;
        }
    }

    private record TrimTemplateObjective(String pattern, Item item) {
    }

    private record PotionObjectiveRow(
            String key,
            net.minecraft.core.Holder<net.minecraft.world.item.alchemy.Potion> potion,
            List<Item> ingredients
    ) {
    }
}
