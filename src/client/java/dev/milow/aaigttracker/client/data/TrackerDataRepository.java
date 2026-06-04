package dev.milow.aaigttracker.client.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.milow.aaigttracker.AllAdvancementsIgtTracker;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class TrackerDataRepository {
    private static final String ROOT = "tracker";
    private static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath(
            AllAdvancementsIgtTracker.MOD_ID,
            "tracker_data"
    );
    private static volatile TrackerData currentData = TrackerData.empty();
    private static boolean registered;

    private TrackerDataRepository() {
    }

    public static TrackerData current() {
        return currentData;
    }

    public static void registerReloadListener() {
        if (registered) {
            return;
        }

        registered = true;
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(RELOAD_ID, new SimpleReloadListener<TrackerData>() {
            @Override
            protected TrackerData prepare(SharedState sharedState) {
                return load(sharedState.resourceManager());
            }

            @Override
            protected void apply(TrackerData prepared, SharedState sharedState) {
                currentData = prepared == null ? TrackerData.empty() : prepared;
            }
        });
    }

    private static TrackerData load(ResourceManager resourceManager) {
        JsonObject advancements = readObject(resourceManager, "advancements.json");
        JsonObject visuals = readObject(resourceManager, "criterion_visuals.json");
        JsonObject items = readObject(resourceManager, "item_objectives.json");
        JsonObject breeding = readObject(resourceManager, "breeding_items.json");
        return new TrackerData(
                parseAdvancementOrderData(advancements),
                parseCriteriaVisualData(visuals),
                parseItemObjectiveData(items),
                parseBreedingItems(breeding)
        );
    }

    private static JsonObject readObject(ResourceManager resourceManager, String filename) {
        Identifier resourceId = Identifier.fromNamespaceAndPath(
                AllAdvancementsIgtTracker.MOD_ID,
                ROOT + "/" + filename
        );

        try {
            var resource = resourceManager.getResource(resourceId);
            if (resource.isPresent()) {
                return readObject(resource.get(), resourceId);
            }
        } catch (RuntimeException exception) {
            AllAdvancementsIgtTracker.LOGGER.warn("Failed to read tracker data resource {}", resourceId, exception);
        }

        List<Resource> stack = resourceManager.getResourceStack(resourceId);
        for (Resource resource : stack) {
            JsonObject parsed = readObject(resource, resourceId);
            if (parsed != null) {
                return parsed;
            }
        }

        AllAdvancementsIgtTracker.LOGGER.warn("Missing tracker data resource {}", resourceId);
        return new JsonObject();
    }

    private static JsonObject readObject(Resource resource, Identifier resourceId) {
        try (BufferedReader reader = resource.openAsReader()) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
            AllAdvancementsIgtTracker.LOGGER.warn("Tracker data resource {} is not a JSON object", resourceId);
        } catch (IOException | RuntimeException exception) {
            AllAdvancementsIgtTracker.LOGGER.warn(
                    "Invalid tracker data resource {} from {}",
                    resourceId,
                    resource.sourcePackId(),
                    exception
            );
        }
        return null;
    }

    private static AdvancementOrderData parseAdvancementOrderData(JsonObject object) {
        return new AdvancementOrderData(
                identifierSet(object, "tracked_roots"),
                identifierList(object, "root_order"),
                identifierSet(object, "hidden_advancements"),
                identifierSet(object, "detailless_advancements"),
                identifierListMap(object, "advancement_order"),
                stringListMap(object, "criterion_order")
        );
    }

    private static CriteriaVisualData parseCriteriaVisualData(JsonObject object) {
        return new CriteriaVisualData(
                componentMap(childObject(object, "cat_sources")),
                componentMap(childObject(object, "wolf_sources")),
                componentMap(childObject(object, "entity_sources")),
                componentMap(childObject(object, "trim_sources")),
                componentMap(childObject(object, "item_info_sources"))
        );
    }

    private static ItemObjectiveData parseItemObjectiveData(JsonObject object) {
        return new ItemObjectiveData(
                parseGates(childObject(object, "gates")),
                parseTrimTemplates(childArray(object, "trim_templates")),
                parsePotions(childArray(object, "potions"))
        );
    }

    private static Map<String, List<Identifier>> parseBreedingItems(JsonObject object) {
        if (object == null) {
            return Map.of();
        }

        Map<String, List<Identifier>> items = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            List<Identifier> itemIds = identifiersFromArray(entry.getValue());
            if (!itemIds.isEmpty()) {
                items.put(entry.getKey(), itemIds);
            }
        }
        return Map.copyOf(items);
    }

    private static Map<Identifier, ItemCompletionGateData> parseGates(JsonObject object) {
        if (object == null) {
            return Map.of();
        }

        Map<Identifier, ItemCompletionGateData> gates = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Identifier objectiveId = parseIdentifier(entry.getKey());
            if (objectiveId == null || !entry.getValue().isJsonArray()) {
                continue;
            }

            List<ItemCompletionGateData.GateRequirementData> requirements = new ArrayList<>();
            for (JsonElement requirementElement : entry.getValue().getAsJsonArray()) {
                if (!requirementElement.isJsonObject()) {
                    continue;
                }

                JsonObject requirementObject = requirementElement.getAsJsonObject();
                Identifier advancementId = identifierMember(requirementObject, "advancement");
                if (advancementId != null) {
                    requirements.add(new ItemCompletionGateData.GateRequirementData(advancementId, stringMember(requirementObject, "criterion")));
                }
            }

            gates.put(objectiveId, new ItemCompletionGateData(requirements));
        }
        return Map.copyOf(gates);
    }

    private static List<TrimTemplateObjectiveData> parseTrimTemplates(JsonArray array) {
        if (array == null) {
            return List.of();
        }

        List<TrimTemplateObjectiveData> templates = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String pattern = stringMember(object, "pattern");
            Identifier itemId = identifierMember(object, "item");
            if (pattern != null && itemId != null) {
                templates.add(new TrimTemplateObjectiveData(pattern, itemId));
            }
        }
        return List.copyOf(templates);
    }

    private static List<PotionObjectiveData> parsePotions(JsonArray array) {
        if (array == null) {
            return List.of();
        }

        List<PotionObjectiveData> potions = new ArrayList<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject object = element.getAsJsonObject();
            String key = stringMember(object, "key");
            Identifier potionId = identifierMember(object, "potion");
            List<Identifier> ingredients = identifiersFromArray(object.get("ingredients"));
            if (key != null && potionId != null) {
                potions.add(new PotionObjectiveData(key, potionId, ingredients));
            }
        }
        return List.copyOf(potions);
    }

    private static Set<Identifier> identifierSet(JsonObject object, String memberName) {
        return new LinkedHashSet<>(identifierList(object, memberName));
    }

    private static List<Identifier> identifierList(JsonObject object, String memberName) {
        if (object == null) {
            return List.of();
        }
        return identifiersFromArray(object.get(memberName));
    }

    private static Map<Identifier, List<Identifier>> identifierListMap(JsonObject object, String memberName) {
        JsonObject child = childObject(object, memberName);
        if (child == null) {
            return Map.of();
        }

        Map<Identifier, List<Identifier>> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : child.entrySet()) {
            Identifier key = parseIdentifier(entry.getKey());
            List<Identifier> ids = identifiersFromArray(entry.getValue());
            if (key != null && !ids.isEmpty()) {
                values.put(key, ids);
            }
        }
        return Map.copyOf(values);
    }

    private static Map<Identifier, List<String>> stringListMap(JsonObject object, String memberName) {
        JsonObject child = childObject(object, memberName);
        if (child == null) {
            return Map.of();
        }

        Map<Identifier, List<String>> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : child.entrySet()) {
            Identifier key = parseIdentifier(entry.getKey());
            List<String> strings = stringsFromArray(entry.getValue());
            if (key != null && !strings.isEmpty()) {
                values.put(key, strings);
            }
        }
        return Map.copyOf(values);
    }

    private static Map<String, Component> componentMap(JsonObject object) {
        if (object == null) {
            return Map.of();
        }

        Map<String, Component> values = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            Component component = component(entry.getValue());
            if (component != null) {
                values.put(entry.getKey(), component);
            }
        }
        return Map.copyOf(values);
    }

    private static Component component(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            return Component.literal(element.getAsString());
        }
        if (!element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        String translate = stringMember(object, "translate");
        if (translate != null) {
            return Component.translatable(translate);
        }

        String text = stringMember(object, "text");
        return text == null ? null : Component.literal(text);
    }

    private static List<Identifier> identifiersFromArray(JsonElement element) {
        List<String> strings = stringsFromArray(element);
        List<Identifier> ids = new ArrayList<>(strings.size());
        for (String string : strings) {
            Identifier id = parseIdentifier(string);
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    private static List<String> stringsFromArray(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }

        List<String> strings = new ArrayList<>();
        for (JsonElement child : element.getAsJsonArray()) {
            if (child != null && child.isJsonPrimitive()) {
                strings.add(child.getAsString());
            }
        }
        return List.copyOf(strings);
    }

    private static Identifier identifierMember(JsonObject object, String memberName) {
        return parseIdentifier(stringMember(object, memberName));
    }

    private static String stringMember(JsonObject object, String memberName) {
        if (object == null || !object.has(memberName)) {
            return null;
        }
        JsonElement element = object.get(memberName);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
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
            return Identifier.parse(value);
        } catch (RuntimeException exception) {
            AllAdvancementsIgtTracker.LOGGER.warn("Ignoring invalid tracker data identifier '{}'", value);
            return null;
        }
    }
}
