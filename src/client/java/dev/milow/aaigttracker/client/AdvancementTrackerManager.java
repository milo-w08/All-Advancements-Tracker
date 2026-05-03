package dev.milow.aaigttracker.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.milow.aaigttracker.AllAdvancementsIgtTracker;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

public final class AdvancementTrackerManager {
    private static final Comparator<AdvancementSnapshot> SNAPSHOT_ORDER = AdvancementOrdering.SNAPSHOT_ORDER;
    private static final Comparator<AdvancementNode> TREE_NODE_ORDER = Comparator
            .comparingDouble(AdvancementTrackerManager::displayX)
            .thenComparingDouble(AdvancementTrackerManager::displayY)
            .thenComparing(node -> node.holder().id().toString());
    private static final FileToIdConverter ADVANCEMENT_RESOURCES = FileToIdConverter.json("advancement");
    private static final String SMITHING_TRIM_SUFFIX = "_smithing_trim";
    private static final String SMITHING_SUFFIX = "_smithing";

    private final SpeedRunIgtBridge speedRunIgtBridge = new SpeedRunIgtBridge();
    private final TimerSource timerSource = new TimerSource(this.speedRunIgtBridge);
    private final Map<WorldKey, WorldTrackerState> worldStates = new HashMap<>();
    private final Map<Identifier, Map<String, CriterionVisual>> criterionVisuals = new HashMap<>();
    private Method progressLookupMethod;
    private WorldKey currentWorldKey;
    private Component inactiveStatusMessage = Component.translatable("screen.aaigttracker.no_world");

    public void tick(Minecraft minecraft) {
        if (minecraft.player == null) {
            this.currentWorldKey = null;
            this.inactiveStatusMessage = Component.translatable("screen.aaigttracker.no_world");
            return;
        }

        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            this.currentWorldKey = null;
            this.inactiveStatusMessage = Component.translatable("screen.aaigttracker.singleplayer_only");
            return;
        }

        WorldKey worldKey = WorldKey.fromServer(server);
        if (worldKey == null) {
            this.currentWorldKey = null;
            this.inactiveStatusMessage = Component.translatable("screen.aaigttracker.load_failed");
            return;
        }

        UUID playerId = minecraft.player.getUUID();
        this.currentWorldKey = worldKey;
        this.inactiveStatusMessage = Component.empty();

        WorldTrackerState worldState = this.worldStates.computeIfAbsent(worldKey, ignored -> new WorldTrackerState());
        worldState.lastTimer = this.selectTimerReading(worldState, this.timerSource.read(minecraft));
        Map<Identifier, SpeedRunIgtBridge.TrackedAdvancement> trackedAdvancements = worldState.speedRunIgtTrusted
                ? this.speedRunIgtBridge.readTrackedAdvancements()
                : Map.of();
        long currentTimerMillis = worldState.lastTimer.available() ? worldState.lastTimer.millis() : -1L;
        Instant referenceInstant = Instant.now();
        this.updateTimerSample(worldState, currentTimerMillis, referenceInstant);

        try {
            Map<Identifier, AdvancementSnapshot> rebuiltSnapshots =
                    server.submit(() -> this.collectSnapshots(server, playerId, trackedAdvancements, worldState)).join();
            worldState.snapshots.clear();
            worldState.snapshots.putAll(rebuiltSnapshots);
            worldState.statusMessage = rebuiltSnapshots.isEmpty()
                    ? Component.translatable("screen.aaigttracker.empty")
                    : Component.empty();
        } catch (CompletionException exception) {
            AllAdvancementsIgtTracker.LOGGER.warn("Failed to capture advancement tracker snapshots from the integrated server", exception);
            this.clearWorldState(worldState);
            worldState.lastTimer = TimerReading.unavailable();
            worldState.statusMessage = Component.translatable("screen.aaigttracker.load_failed");
        }
    }

    public List<AdvancementSnapshot> getSnapshots() {
        return this.activeWorldState().snapshots.values().stream()
                .sorted(SNAPSHOT_ORDER)
                .toList();
    }

    public int getCompletedCount() {
        return (int) this.activeWorldState().snapshots.values().stream().filter(AdvancementSnapshot::done).count();
    }

    public int getTotalCount() {
        return this.activeWorldState().snapshots.size();
    }

    public TimerReading getLastTimer() {
        return this.activeWorldState().lastTimer;
    }

    public Component getStatusMessage() {
        if (this.currentWorldKey == null) {
            return this.inactiveStatusMessage;
        }

        return this.activeWorldState().statusMessage;
    }

    public WorldKey getCurrentWorldKey() {
        return this.currentWorldKey;
    }

    private Map<Identifier, AdvancementSnapshot> collectSnapshots(
            IntegratedServer server,
            UUID playerId,
            Map<Identifier, SpeedRunIgtBridge.TrackedAdvancement> trackedAdvancements,
            WorldTrackerState worldState
    ) {
        ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
        if (serverPlayer == null) {
            return Map.of();
        }

        AdvancementTree tree = server.getAdvancements().tree();
        ResourceManager resourceManager = server.getResourceManager();
        Map<Identifier, AdvancementSnapshot> rebuiltSnapshots = new HashMap<>();

        List<AdvancementNode> roots = copyNodes(tree.roots());
        roots.removeIf(node -> !AdvancementOrdering.isTrackedRunAdvancement(node.holder().id()));
        roots.sort(Comparator.comparingInt(node -> AdvancementOrdering.rootOrder(node.holder().id())));

        List<ResolvedAdvancement> resolvedAdvancements = new ArrayList<>();
        int nextTreeIndex = 0;
        for (AdvancementNode root : roots) {
            nextTreeIndex = this.collectResolvedAdvancements(
                    serverPlayer,
                    root,
                    trackedAdvancements,
                    resolvedAdvancements,
                    nextTreeIndex
            );
        }

        CriterionTimeEstimator timeEstimator = CriterionTimeEstimator.create(
                resolvedAdvancements,
                worldState.lastAdvancingTimerSample
        );
        for (ResolvedAdvancement resolvedAdvancement : resolvedAdvancements) {
            rebuiltSnapshots.put(
                    resolvedAdvancement.id(),
                    this.buildSnapshot(resourceManager, resolvedAdvancement, timeEstimator, worldState)
            );
        }

        return rebuiltSnapshots;
    }

    private int collectResolvedAdvancements(
            ServerPlayer serverPlayer,
            AdvancementNode node,
            Map<Identifier, SpeedRunIgtBridge.TrackedAdvancement> trackedAdvancements,
            List<ResolvedAdvancement> resolvedAdvancements,
            int nextTreeIndex
    ) {
        AdvancementHolder holder = node.holder();
        Identifier id = holder.id();
        if (!AdvancementOrdering.isTrackedRunAdvancement(id)) {
            return nextTreeIndex;
        }

        Advancement advancement = holder.value();
        Optional<DisplayInfo> displayInfo = advancement.display();
        if (displayInfo.isPresent() && !TrackerDataRepository.current().advancements().hiddenAdvancements().contains(id)) {
            AdvancementProgress progress = this.resolveProgress(serverPlayer, holder, advancement);
            resolvedAdvancements.add(
                    new ResolvedAdvancement(
                            id,
                            node,
                            advancement,
                            progress,
                            displayInfo.orElseThrow(),
                            trackedAdvancements.get(id),
                            nextTreeIndex
                    )
            );
            nextTreeIndex++;
        }

        List<AdvancementNode> children = copyNodes(node.children());
        children.sort(TREE_NODE_ORDER);
        for (AdvancementNode child : children) {
            nextTreeIndex = this.collectResolvedAdvancements(
                    serverPlayer,
                    child,
                    trackedAdvancements,
                    resolvedAdvancements,
                    nextTreeIndex
            );
        }

        return nextTreeIndex;
    }

    private AdvancementSnapshot buildSnapshot(
            ResourceManager resourceManager,
            ResolvedAdvancement resolvedAdvancement,
            CriterionTimeEstimator timeEstimator,
            WorldTrackerState worldState
    ) {
        Identifier id = resolvedAdvancement.id();
        Advancement advancement = resolvedAdvancement.advancement();
        AdvancementProgress progress = resolvedAdvancement.progress();
        DisplayInfo displayInfo = resolvedAdvancement.displayInfo();
        List<List<String>> requirementKeys = copyRequirementKeys(advancement.requirements().requirements());
        List<String> orderedCriteria = orderedCriteria(id, advancement, requirementKeys);
        List<String> completedCriteria = copyCriteria(progress.getCompletedCriteria());
        Set<String> completedSet = Set.copyOf(completedCriteria);
        Map<String, CriterionVisual> visuals = this.loadCriterionVisuals(resourceManager, id);

        Map<String, CriterionSnapshot> criteriaByKey = new LinkedHashMap<>(orderedCriteria.size());
        for (String criterion : orderedCriteria) {
            CriterionVisual visual = visuals.get(criterion);
            if (visual == null) {
                visual = CriterionVisualResolver.decorate(id, criterion, this.fallbackCriterionVisual(id, criterion));
            }
            Long criterionCompletionTime = this.criterionCompletionTime(worldState, id, criterion, progress, timeEstimator);
            criteriaByKey.put(criterion, new CriterionSnapshot(
                    criterion,
                    visual.displayName(),
                    visual.icon(),
                    visual.supplementaryIcons(),
                    visual.supplementaryIconMode(),
                    completedSet.contains(criterion),
                    criterionCompletionTime,
                    true
            ));
        }

        List<RequirementGroupSnapshot> requirementGroups = new ArrayList<>(requirementKeys.size());
        for (List<String> groupKeys : requirementKeys) {
            List<CriterionSnapshot> groupedCriteria = new ArrayList<>(groupKeys.size());
            for (String criterionKey : groupKeys) {
                CriterionSnapshot criterion = criteriaByKey.get(criterionKey);
                if (criterion != null) {
                    groupedCriteria.add(criterion);
                }
            }

            if (!groupedCriteria.isEmpty()) {
                requirementGroups.add(new RequirementGroupSnapshot(List.copyOf(groupedCriteria)));
            }
        }

        AdvancementNode rootNode = resolvedAdvancement.node().root();
        Identifier rootId = rootNode.holder().id();
        Component rootTitle = rootNode.holder().value().display()
                .map(DisplayInfo::getTitle)
                .orElse(Component.literal(humanizeRoot(rootId)));
        ItemStack rootIcon = rootNode.holder().value().display()
                .map(info -> info.getIcon().create())
                .orElse(ItemStack.EMPTY);
        boolean done = progress.isDone();
        Long completionTime = resolvedAdvancement.trackedAdvancement() != null && resolvedAdvancement.trackedAdvancement().complete()
                ? resolvedAdvancement.trackedAdvancement().igtMillis()
                : null;

        return new AdvancementSnapshot(
                id,
                rootId,
                rootTitle,
                rootIcon,
                displayInfo.getTitle(),
                displayInfo.getDescription(),
                displayInfo.getIcon().create(),
                done,
                progress.getPercent(),
                displayInfo.getX(),
                displayInfo.getY(),
                orderedCriteria.size(),
                completedCriteria.size(),
                List.copyOf(criteriaByKey.values()),
                List.copyOf(requirementGroups),
                resolvedAdvancement.treeIndex(),
                completionTime
        );
    }

    private Map<String, CriterionVisual> loadCriterionVisuals(ResourceManager resourceManager, Identifier advancementId) {
        return this.criterionVisuals.computeIfAbsent(advancementId, id -> this.readCriterionVisuals(resourceManager, id));
    }

    private Map<String, CriterionVisual> readCriterionVisuals(ResourceManager resourceManager, Identifier advancementId) {
        Identifier resourceId = ADVANCEMENT_RESOURCES.idToFile(advancementId);
        try (Reader reader = resourceManager.openAsReader(resourceId)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) {
                return Map.of();
            }

            JsonObject root = parsed.getAsJsonObject();
            JsonObject criteriaObject = childObject(root, "criteria");
            if (criteriaObject == null) {
                return Map.of();
            }

            Map<String, CriterionVisual> visuals = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : criteriaObject.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }

                CriterionVisual visual = this.parseCriterionVisual(advancementId, entry.getKey(), entry.getValue().getAsJsonObject());
                visuals.put(entry.getKey(), CriterionVisualResolver.decorate(
                        advancementId,
                        entry.getKey(),
                        visual
                ));
            }

            return Map.copyOf(visuals);
        } catch (IOException | IllegalStateException exception) {
            AllAdvancementsIgtTracker.LOGGER.debug("Failed to read criterion visuals for {}", advancementId, exception);
            return Map.of();
        }
    }

    private CriterionVisual parseCriterionVisual(Identifier advancementId, String criterionKey, JsonObject criterionJson) {
        JsonObject conditions = childObject(criterionJson, "conditions");
        if (conditions != null) {
            CriterionVisual entityVisual = this.parseEntityVisual(advancementId, criterionKey, conditions);
            if (entityVisual != null) {
                return entityVisual;
            }

            CriterionVisual itemVisual = this.parseItemVisual(criterionKey, conditions);
            if (itemVisual != null) {
                return itemVisual;
            }

            CriterionVisual blockVisual = this.parseBlockVisual(criterionKey, conditions);
            if (blockVisual != null) {
                return blockVisual;
            }

            CriterionVisual recipeVisual = this.parseRecipeVisual(criterionKey, conditions);
            if (recipeVisual != null) {
                return recipeVisual;
            }

            CriterionVisual biomeVisual = this.parseBiomeVisual(advancementId, criterionKey, conditions);
            if (biomeVisual != null) {
                return biomeVisual;
            }
        }

        return this.fallbackCriterionVisual(advancementId, criterionKey);
    }

    private CriterionVisual parseEntityVisual(Identifier advancementId, String criterionKey, JsonObject conditions) {
        for (String fieldName : List.of("entity", "child", "parent", "partner")) {
            JsonArray targets = childArray(conditions, fieldName);
            if (targets == null || targets.isEmpty() || !targets.get(0).isJsonObject()) {
                continue;
            }

            JsonObject targetObject = targets.get(0).getAsJsonObject();
            JsonObject predicate = childObject(targetObject, "predicate");
            if (predicate == null) {
                continue;
            }

            Identifier entityId = stringIdentifier(predicate, "type");
            boolean preferCriterionLabel = false;
            if (entityId == null) {
                JsonObject components = childObject(predicate, "components");
                if (components != null) {
                    for (Map.Entry<String, JsonElement> componentEntry : components.entrySet()) {
                        int variantIndex = componentEntry.getKey().indexOf("/variant");
                        if (variantIndex > 0) {
                            entityId = parseIdentifier(componentEntry.getKey().substring(0, variantIndex));
                            preferCriterionLabel = true;
                            break;
                        }
                    }
                }
            } else {
                Identifier criterionId = parseIdentifier(criterionKey);
                preferCriterionLabel = criterionId == null || !criterionId.equals(entityId);
            }

            if (entityId != null) {
                return this.visualForEntity(
                        advancementId,
                        entityId,
                        criterionKey,
                        preferCriterionLabel ? Component.literal(humanizeCriterion(criterionKey)) : null
                );
            }
        }

        return null;
    }

    private CriterionVisual parseItemVisual(String criterionKey, JsonObject conditions) {
        JsonObject itemObject = childObject(conditions, "item");
        if (itemObject != null) {
            Identifier itemId = firstItemIdentifier(itemObject);
            CriterionVisual visual = this.visualForItem(itemId);
            if (visual != null) {
                return visual;
            }
        }

        JsonArray itemArray = childArray(conditions, "items");
        if (itemArray != null) {
            for (JsonElement element : itemArray) {
                if (element.isJsonObject()) {
                    Identifier itemId = firstItemIdentifier(element.getAsJsonObject());
                    CriterionVisual visual = this.visualForItem(itemId);
                    if (visual != null) {
                        return visual;
                    }
                }
            }
        }

        CriterionVisual fallback = this.visualForItem(parseIdentifier(criterionKey));
        if (fallback != null) {
            return fallback;
        }

        return null;
    }

    private CriterionVisual parseBlockVisual(String criterionKey, JsonObject conditions) {
        Identifier blockId = null;

        JsonObject locationObject = childObject(conditions, "location");
        if (locationObject != null) {
            blockId = stringIdentifier(locationObject, "block");
        }

        JsonArray locationArray = childArray(conditions, "location");
        if (blockId == null && locationArray != null) {
            for (JsonElement element : locationArray) {
                if (!element.isJsonObject()) {
                    continue;
                }

                blockId = stringIdentifier(element.getAsJsonObject(), "block");
                if (blockId != null) {
                    break;
                }
            }
        }

        if (blockId == null) {
            return null;
        }

        Identifier itemId = representativeItemForBlock(blockId);
        Component overrideLabel = !itemId.equals(blockId)
                ? Component.literal(humanizeCriterion(criterionKey))
                : null;
        return this.visualForItem(itemId, overrideLabel);
    }

    private CriterionVisual parseRecipeVisual(String criterionKey, JsonObject conditions) {
        Identifier recipeId = stringIdentifier(conditions, "recipe_id");
        if (recipeId == null) {
            return null;
        }

        CriterionVisual visual = this.visualForItem(this.recipeOutputIdentifier(recipeId));
        if (visual != null) {
            return visual;
        }

        if (criterionKey.startsWith("armor_trimmed_")) {
            int colonIndex = criterionKey.indexOf(':');
            String path = colonIndex >= 0 ? criterionKey.substring(colonIndex + 1) : criterionKey;
            if (path.startsWith("armor_trimmed_")) {
                Identifier itemId = parseIdentifier("minecraft:" + path.substring("armor_trimmed_".length()));
                visual = this.visualForItem(this.recipeOutputIdentifier(itemId));
                if (visual != null) {
                    return visual;
                }
            }
        }

        return null;
    }

    private CriterionVisual parseBiomeVisual(Identifier advancementId, String criterionKey, JsonObject conditions) {
        JsonArray players = childArray(conditions, "player");
        if (players == null || players.isEmpty() || !players.get(0).isJsonObject()) {
            return null;
        }

        JsonObject playerObject = players.get(0).getAsJsonObject();
        JsonObject predicate = childObject(playerObject, "predicate");
        JsonObject location = predicate != null ? childObject(predicate, "location") : null;
        if (location == null) {
            return null;
        }

        Identifier biomeId = stringIdentifier(location, "biomes");
        if (biomeId == null) {
            return null;
        }

        return this.visualForBiome(biomeId, isAdventuringTime(advancementId));
    }

    private CriterionVisual fallbackCriterionVisual(Identifier advancementId, String criterionKey) {
        if (isAdventuringTime(advancementId)) {
            CriterionVisual biomeVisual = this.visualForBiome(parseIdentifier(criterionKey), true);
            if (biomeVisual != null) {
                return biomeVisual;
            }
        }

        CriterionVisual itemVisual = this.visualForItem(parseIdentifier(criterionKey));
        if (itemVisual != null) {
            return itemVisual;
        }

        CriterionVisual entityVisual = this.visualForEntity(advancementId, parseIdentifier(criterionKey), criterionKey, null);
        if (entityVisual != null) {
            return entityVisual;
        }

        CriterionVisual effectVisual = this.visualForEffect(parseIdentifier(criterionKey));
        if (effectVisual != null) {
            return effectVisual;
        }

        return new CriterionVisual(
                Component.literal(humanizeCriterion(criterionKey)),
                CriterionIcon.item(ItemStack.EMPTY),
                List.of(),
                SupplementaryIconMode.NONE
        );
    }

    private CriterionVisual visualForItem(Identifier itemId) {
        return this.visualForItem(itemId, null);
    }

    private CriterionVisual visualForItem(Identifier itemId, Component overrideLabel) {
        if (itemId == null) {
            return null;
        }

        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null || item == Items.AIR) {
            return null;
        }

        ItemStack stack = item.getDefaultInstance();
        Component label = overrideLabel != null ? overrideLabel : item.getName(stack);
        return new CriterionVisual(label, CriterionIcon.item(stack), List.of(), SupplementaryIconMode.NONE);
    }

    private CriterionVisual visualForEntity(
            Identifier advancementId,
            Identifier entityId,
            String criterionKey,
            Component overrideLabel
    ) {
        if (entityId == null) {
            return null;
        }

        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).orElse(null);
        if (entityType == null) {
            return null;
        }

        ItemStack fallbackItem = SpawnEggItem.byId(entityType)
                .map(holder -> holder.value().getDefaultInstance())
                .orElse(Items.NAME_TAG.getDefaultInstance());
        Component label = overrideLabel != null ? overrideLabel : entityType.getDescription();
        EntityPreviewSpec previewSpec = this.entityPreviewSpec(advancementId, entityId, criterionKey);
        CriterionIcon icon = previewSpec != null
                ? CriterionIcon.entity(fallbackItem, previewSpec)
                : CriterionIcon.item(fallbackItem);
        return new CriterionVisual(label, icon, List.of(), SupplementaryIconMode.NONE);
    }

    private CriterionVisual visualForBiome(Identifier biomeId, boolean usePreview) {
        if (biomeId == null) {
            return null;
        }

        ItemStack fallbackIcon = this.itemStackOrDefault(biomeIconId(biomeId.getPath()), Items.GRASS_BLOCK);
        CriterionIcon icon = CriterionIcon.item(fallbackIcon);
        if (usePreview) {
            icon = CriterionIcon.biome(fallbackIcon, new BiomePreviewSpec(biomeId, List.of()));
        }

        return new CriterionVisual(
                Component.translatable("biome." + biomeId.getNamespace() + "." + biomeId.getPath().replace('/', '.')),
                icon,
                List.of(),
                SupplementaryIconMode.NONE
        );
    }

    private CriterionVisual visualForEffect(Identifier effectId) {
        if (effectId == null) {
            return null;
        }

        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getOptional(effectId).orElse(null);
        if (effect == null) {
            return null;
        }

        return new CriterionVisual(
                Component.translatable(effect.getDescriptionId()),
                CriterionIcon.item(Items.POTION.getDefaultInstance()),
                List.of(),
                SupplementaryIconMode.NONE
        );
    }

    private Long criterionCompletionTime(
            WorldTrackerState worldState,
            Identifier advancementId,
            String criterion,
            AdvancementProgress progress,
            CriterionTimeEstimator timeEstimator
    ) {
        Instant obtainedAt = criterionObtained(progress, criterion);
        if (obtainedAt == null) {
            return null;
        }

        CriterionCacheKey cacheKey = new CriterionCacheKey(advancementId, criterion);
        Long cachedTime = worldState.criterionCompletionTimes.get(cacheKey);
        if (cachedTime != null) {
            return cachedTime;
        }

        Long estimatedTime = timeEstimator.estimate(obtainedAt);
        if (estimatedTime != null) {
            worldState.criterionCompletionTimes.put(cacheKey, estimatedTime);
        }
        return estimatedTime;
    }

    private void updateTimerSample(WorldTrackerState worldState, long currentTimerMillis, Instant referenceInstant) {
        if (currentTimerMillis < 0L || referenceInstant == null) {
            return;
        }

        if (worldState.lastObservedTimerMillis != null && currentTimerMillis < worldState.lastObservedTimerMillis) {
            this.clearTimingState(worldState);
        }

        if (worldState.lastAdvancingTimerSample == null || currentTimerMillis > worldState.lastAdvancingTimerSample.igtMillis()) {
            worldState.lastAdvancingTimerSample = new TimeSample(referenceInstant, currentTimerMillis);
        }

        worldState.lastObservedTimerMillis = currentTimerMillis;
    }

    private void clearTimingState(WorldTrackerState worldState) {
        worldState.criterionCompletionTimes.clear();
        worldState.lastAdvancingTimerSample = null;
        worldState.lastObservedTimerMillis = null;
    }

    private void clearWorldState(WorldTrackerState worldState) {
        worldState.snapshots.clear();
        this.clearTimingState(worldState);
        worldState.speedRunIgtTrusted = false;
    }

    private static Identifier recipeOutputIdentifier(Identifier recipeId) {
        if (recipeId == null) {
            return null;
        }

        String path = recipeId.getPath();
        if (path.endsWith(SMITHING_TRIM_SUFFIX)) {
            return Identifier.fromNamespaceAndPath(recipeId.getNamespace(), path.substring(0, path.length() - SMITHING_TRIM_SUFFIX.length()));
        }

        if (path.endsWith(SMITHING_SUFFIX)) {
            return Identifier.fromNamespaceAndPath(recipeId.getNamespace(), path.substring(0, path.length() - SMITHING_SUFFIX.length()));
        }

        return recipeId;
    }

    private static Identifier firstItemIdentifier(JsonObject itemObject) {
        return stringIdentifier(itemObject, "items");
    }

    private static Identifier stringIdentifier(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName)) {
            return null;
        }

        JsonElement element = object.get(memberName);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return parseIdentifier(element.getAsString());
        }

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    return parseIdentifier(child.getAsString());
                }
            }
        }

        return null;
    }

    private static JsonObject childObject(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || !object.get(memberName).isJsonObject()) {
            return null;
        }

        return object.getAsJsonObject(memberName);
    }

    private static JsonArray childArray(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName) || !object.get(memberName).isJsonArray()) {
            return null;
        }

        return object.getAsJsonArray(memberName);
    }

    private static Identifier parseIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return value.indexOf(':') >= 0
                    ? Identifier.parse(value)
                    : Identifier.fromNamespaceAndPath("minecraft", value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static List<String> copyCriteria(Iterable<String> criteria) {
        List<String> values = new ArrayList<>();
        for (String criterion : criteria) {
            values.add(criterion);
        }
        return values;
    }

    private static String stripNamespace(String rawKey) {
        if (rawKey == null) {
            return null;
        }

        int separatorIndex = rawKey.indexOf(':');
        return separatorIndex >= 0 ? rawKey.substring(separatorIndex + 1) : rawKey;
    }

    private static Instant criterionObtained(AdvancementProgress progress, String criterion) {
        if (progress == null || criterion == null) {
            return null;
        }

        var criterionProgress = progress.getCriterion(criterion);
        if (criterionProgress == null || !criterionProgress.isDone()) {
            return null;
        }

        return criterionProgress.getObtained();
    }

    private static List<List<String>> copyRequirementKeys(List<List<String>> requirementGroups) {
        List<List<String>> values = new ArrayList<>(requirementGroups.size());
        for (List<String> group : requirementGroups) {
            values.add(List.copyOf(group));
        }
        return values;
    }

    private static List<String> orderedCriteria(Identifier advancementId, Advancement advancement, List<List<String>> requirementGroups) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (List<String> group : requirementGroups) {
            for (String criterion : group) {
                seen.putIfAbsent(criterion, Boolean.TRUE);
            }
        }

        for (String criterion : advancement.criteria().keySet()) {
            seen.putIfAbsent(criterion, Boolean.TRUE);
        }

        return AdvancementOrdering.orderCriteria(advancementId, new ArrayList<>(seen.keySet()));
    }

    private AdvancementProgress resolveProgress(
            ServerPlayer serverPlayer,
            AdvancementHolder holder,
            Advancement advancement
    ) {
        try {
            if (this.progressLookupMethod == null) {
                this.progressLookupMethod = resolveProgressLookupMethod(serverPlayer.getAdvancements().getClass());
            }

            if (this.progressLookupMethod != null) {
                Object value = this.progressLookupMethod.invoke(serverPlayer.getAdvancements(), holder);
                if (value instanceof AdvancementProgress progress) {
                    return progress;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            AllAdvancementsIgtTracker.LOGGER.debug("Failed to read live advancement progress for {}", holder.id(), exception);
        }

        AdvancementProgress emptyProgress = new AdvancementProgress();
        emptyProgress.update(advancement.requirements());
        return emptyProgress;
    }

    private static Method resolveProgressLookupMethod(Class<?> type) {
        for (String name : List.of("getOrStartProgress", "getProgress", "getOrCreateProgress")) {
            try {
                return type.getMethod(name, AdvancementHolder.class);
            } catch (NoSuchMethodException ignored) {
            }
        }

        return null;
    }

    private WorldTrackerState activeWorldState() {
        if (this.currentWorldKey == null) {
            return WorldTrackerState.EMPTY;
        }

        return this.worldStates.computeIfAbsent(this.currentWorldKey, ignored -> new WorldTrackerState());
    }

    private TimerReading selectTimerReading(WorldTrackerState worldState, TimerReading rawReading) {
        if (!rawReading.hasSpeedRunIgtMillis()) {
            if (worldState.speedRunIgtTrusted) {
                this.clearTimingState(worldState);
            }
            worldState.speedRunIgtTrusted = false;
            return rawReading.hasFallbackLevelMillis()
                    ? rawReading.withDisplay(true, rawReading.fallbackLevelMillis(), "screen.aaigttracker.timer.fallback")
                    : TimerReading.unavailable();
        }

        Long speedRunIgtMillis = rawReading.speedRunIgtMillis();
        Long fallbackLevelMillis = rawReading.fallbackLevelMillis();
        boolean alignedToWorld = fallbackLevelMillis == null || speedRunIgtMillis <= fallbackLevelMillis + 5_000L;

        if (worldState.speedRunIgtTrusted && !alignedToWorld) {
            this.clearTimingState(worldState);
            worldState.speedRunIgtTrusted = false;
        }

        if (!worldState.speedRunIgtTrusted && alignedToWorld) {
            worldState.speedRunIgtTrusted = true;
        }

        if (worldState.speedRunIgtTrusted) {
            return rawReading.withDisplay(true, speedRunIgtMillis, "screen.aaigttracker.timer.speedrunigt");
        }

        return fallbackLevelMillis != null
                ? rawReading.withDisplay(true, fallbackLevelMillis, "screen.aaigttracker.timer.fallback")
                : TimerReading.unavailable();
    }

    private static List<AdvancementNode> copyNodes(Iterable<AdvancementNode> nodes) {
        List<AdvancementNode> values = new ArrayList<>();
        for (AdvancementNode node : nodes) {
            values.add(node);
        }
        return values;
    }

    private static Identifier representativeItemForBlock(Identifier blockId) {
        if (blockId == null) {
            return null;
        }

        return switch (blockId.getPath()) {
            case "beetroots" -> id("minecraft:beetroot_seeds");
            case "melon_stem", "attached_melon_stem" -> id("minecraft:melon_seeds");
            case "pumpkin_stem", "attached_pumpkin_stem" -> id("minecraft:pumpkin_seeds");
            case "pitcher_crop" -> id("minecraft:pitcher_pod");
            case "torchflower_crop" -> id("minecraft:torchflower_seeds");
            default -> blockId;
        };
    }

    private static double displayX(AdvancementNode node) {
        return node.holder().value().display()
                .map(DisplayInfo::getX)
                .orElse(Float.MAX_VALUE);
    }

    private static double displayY(AdvancementNode node) {
        return node.holder().value().display()
                .map(DisplayInfo::getY)
                .orElse(Float.MAX_VALUE);
    }

    private EntityPreviewSpec entityPreviewSpec(Identifier advancementId, Identifier entityId, String criterionKey) {
        if (advancementId == null || entityId == null || criterionKey == null) {
            return null;
        }

        String keyPath = stripNamespace(criterionKey);
        return switch (advancementId.toString()) {
            case "minecraft:husbandry/complete_catalogue" ->
                    EntityPreviewSpec.cat(id("minecraft:" + keyPath));
            case "minecraft:husbandry/whole_pack" ->
                    EntityPreviewSpec.wolf(id("minecraft:" + keyPath));
            case "minecraft:husbandry/leash_all_frog_variants" ->
                    EntityPreviewSpec.frog(id("minecraft:" + keyPath));
            case "minecraft:husbandry/bred_all_animals",
                    "minecraft:adventure/kill_all_mobs",
                    "minecraft:adventure/kill_a_mob" ->
                    EntityPreviewSpec.entity(entityId);
            default -> null;
        };
    }

    private static String humanizeRoot(Identifier rootId) {
        String path = rootId.getPath();
        int slash = path.indexOf('/');
        String raw = slash >= 0 ? path.substring(0, slash) : path;
        return humanizeCriterion(raw);
    }

    private static String humanizeCriterion(String rawCriterion) {
        String value = rawCriterion;
        int colonIndex = value.indexOf(':');
        if (colonIndex >= 0 && colonIndex + 1 < value.length()) {
            value = value.substring(colonIndex + 1);
        }

        value = value.replace('/', ' ').replace('_', ' ').replace('.', ' ');
        String[] words = value.split(" +");
        StringBuilder builder = new StringBuilder(value.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (builder.length() > 0) {
                builder.append(' ');
            }

            builder.append(word.substring(0, 1).toUpperCase(Locale.ROOT));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }

        return builder.isEmpty() ? rawCriterion : builder.toString();
    }

    private ItemStack itemStackOrDefault(Identifier itemId, Item fallbackItem) {
        Item item = itemId != null
                ? BuiltInRegistries.ITEM.getOptional(itemId).orElse(null)
                : null;
        if (item == null || item == Items.AIR) {
            item = fallbackItem;
        }

        return item.getDefaultInstance();
    }

    private static Identifier biomeIconId(String biomePath) {
        if (biomePath == null || biomePath.isBlank()) {
            return null;
        }

        return switch (biomePath) {
            case "nether_wastes" -> id("minecraft:netherrack");
            case "soul_sand_valley" -> id("minecraft:soul_sand");
            case "crimson_forest" -> id("minecraft:crimson_fungus");
            case "warped_forest" -> id("minecraft:warped_fungus");
            case "basalt_deltas" -> id("minecraft:basalt");
            case "mushroom_fields" -> id("minecraft:red_mushroom");
            case "deep_frozen_ocean" -> id("minecraft:blue_ice");
            case "frozen_ocean" -> id("minecraft:ice");
            case "deep_cold_ocean", "cold_ocean" -> id("minecraft:kelp");
            case "deep_ocean", "ocean", "river" -> id("minecraft:kelp");
            case "deep_lukewarm_ocean", "lukewarm_ocean", "warm_ocean" -> id("minecraft:sea_pickle");
            case "frozen_river" -> id("minecraft:ice");
            case "stony_shore", "windswept_hills", "windswept_forest", "windswept_gravelly_hills", "jagged_peaks", "stony_peaks" ->
                    id("minecraft:stone");
            case "swamp" -> id("minecraft:lily_pad");
            case "mangrove_swamp" -> id("minecraft:mangrove_propagule");
            case "snowy_slopes", "snowy_plains", "snowy_taiga" -> id("minecraft:snowball");
            case "ice_spikes", "frozen_peaks" -> id("minecraft:packed_ice");
            case "grove", "taiga", "old_growth_spruce_taiga", "old_growth_pine_taiga" -> id("minecraft:spruce_sapling");
            case "plains", "sunflower_plains" -> id("minecraft:sunflower");
            case "meadow" -> id("minecraft:oxeye_daisy");
            case "beach", "snowy_beach" -> id("minecraft:sand");
            case "forest" -> id("minecraft:oak_sapling");
            case "flower_forest" -> id("minecraft:dandelion");
            case "birch_forest", "old_growth_birch_forest" -> id("minecraft:birch_sapling");
            case "dark_forest" -> id("minecraft:dark_oak_sapling");
            case "pale_garden" -> id("minecraft:pale_oak_sapling");
            case "savanna", "savanna_plateau", "windswept_savanna" -> id("minecraft:acacia_sapling");
            case "jungle", "sparse_jungle" -> id("minecraft:jungle_sapling");
            case "bamboo_jungle" -> id("minecraft:bamboo");
            case "badlands", "wooded_badlands", "eroded_badlands" -> id("minecraft:red_sand");
            case "desert" -> id("minecraft:cactus");
            case "cherry_grove" -> id("minecraft:cherry_sapling");
            case "dripstone_caves" -> id("minecraft:pointed_dripstone");
            case "lush_caves" -> id("minecraft:glow_berries");
            case "deep_dark" -> id("minecraft:sculk");
            default -> id("minecraft:grass_block");
        };
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }

    private static boolean isAdventuringTime(Identifier advancementId) {
        return advancementId != null && "minecraft:adventure/adventuring_time".equals(advancementId.toString());
    }

    private record ResolvedAdvancement(
            Identifier id,
            AdvancementNode node,
            Advancement advancement,
            AdvancementProgress progress,
            DisplayInfo displayInfo,
            SpeedRunIgtBridge.TrackedAdvancement trackedAdvancement,
            int treeIndex
    ) {
    }

    private static final class CriterionTimeEstimator {
        private final List<TimeSample> samples;

        private CriterionTimeEstimator(List<TimeSample> samples) {
            this.samples = samples;
        }

        private static CriterionTimeEstimator create(List<ResolvedAdvancement> resolvedAdvancements, TimeSample liveTimerSample) {
            List<TimeSample> samples = new ArrayList<>();
            if (liveTimerSample != null) {
                samples.add(liveTimerSample);
            }

            for (ResolvedAdvancement resolvedAdvancement : resolvedAdvancements) {
                SpeedRunIgtBridge.TrackedAdvancement trackedAdvancement = resolvedAdvancement.trackedAdvancement();
                if (trackedAdvancement == null || !trackedAdvancement.complete() || !trackedAdvancement.hasKnownIgt()) {
                    continue;
                }

                Instant completionInstant = latestCriterionObtained(
                        resolvedAdvancement.progress(),
                        resolvedAdvancement.advancement().criteria().keySet()
                );
                if (completionInstant != null) {
                    samples.add(new TimeSample(completionInstant, trackedAdvancement.igtMillis()));
                }
            }

            samples.sort(Comparator
                    .comparing(TimeSample::instant)
                    .thenComparingLong(TimeSample::igtMillis));
            return new CriterionTimeEstimator(List.copyOf(samples));
        }

        private Long estimate(Instant criterionInstant) {
            if (criterionInstant == null || this.samples.isEmpty()) {
                return null;
            }

            if (this.samples.size() == 1) {
                return estimateWithSegment(this.samples.get(0), this.samples.get(0), criterionInstant);
            }

            TimeSample previous = null;
            for (TimeSample sample : this.samples) {
                if (!sample.instant().isBefore(criterionInstant)) {
                    if (previous == null) {
                        return estimateWithSegment(sample, this.samples.get(1), criterionInstant);
                    }

                    return estimateWithSegment(previous, sample, criterionInstant);
                }
                previous = sample;
            }

            TimeSample last = this.samples.get(this.samples.size() - 1);
            TimeSample beforeLast = this.samples.get(this.samples.size() - 2);
            return estimateWithSegment(beforeLast, last, criterionInstant);
        }

        private static Long estimateWithSegment(TimeSample start, TimeSample end, Instant target) {
            if (start == null || end == null || target == null) {
                return null;
            }

            if (start.instant().equals(end.instant())) {
                long deltaMillis = Duration.between(target, end.instant()).toMillis();
                return Math.max(0L, end.igtMillis() - deltaMillis);
            }

            long segmentWallMillis = Duration.between(start.instant(), end.instant()).toMillis();
            if (segmentWallMillis == 0L) {
                return Math.max(0L, start.igtMillis());
            }

            long elapsedWallMillis = Duration.between(start.instant(), target).toMillis();
            double ratio = elapsedWallMillis / (double) segmentWallMillis;
            long estimate = Math.round(start.igtMillis() + ratio * (end.igtMillis() - start.igtMillis()));
            return Math.max(0L, estimate);
        }

        private static Instant latestCriterionObtained(AdvancementProgress progress, Iterable<String> criteria) {
            Instant latest = null;
            for (String criterion : criteria) {
                Instant obtained = criterionObtained(progress, criterion);
                if (obtained != null && (latest == null || obtained.isAfter(latest))) {
                    latest = obtained;
                }
            }
            return latest;
        }
    }

    private record CriterionCacheKey(Identifier advancementId, String criterionKey) {
    }

    private record TimeSample(Instant instant, long igtMillis) {
    }

    private static final class WorldTrackerState {
        private static final WorldTrackerState EMPTY = createEmpty();

        private final Map<Identifier, AdvancementSnapshot> snapshots = new HashMap<>();
        private final Map<CriterionCacheKey, Long> criterionCompletionTimes = new HashMap<>();
        private TimerReading lastTimer = TimerReading.unavailable();
        private Component statusMessage = Component.translatable("screen.aaigttracker.empty");
        private TimeSample lastAdvancingTimerSample;
        private Long lastObservedTimerMillis;
        private boolean speedRunIgtTrusted;

        private static WorldTrackerState createEmpty() {
            WorldTrackerState state = new WorldTrackerState();
            state.statusMessage = Component.translatable("screen.aaigttracker.no_world");
            return state;
        }
    }
}
