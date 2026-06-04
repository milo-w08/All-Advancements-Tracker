package dev.milow.aaigttracker.client.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

public record AdvancementOrderData(
        Set<Identifier> trackedRoots,
        List<Identifier> rootOrder,
        Set<Identifier> hiddenAdvancements,
        Set<Identifier> detaillessAdvancements,
        Map<Identifier, List<Identifier>> advancementOrders,
        Map<Identifier, List<String>> criterionOrders
) {
    public AdvancementOrderData {
        trackedRoots = trackedRoots == null ? Set.of() : Set.copyOf(trackedRoots);
        rootOrder = rootOrder == null ? List.of() : List.copyOf(rootOrder);
        hiddenAdvancements = hiddenAdvancements == null ? Set.of() : Set.copyOf(hiddenAdvancements);
        detaillessAdvancements = detaillessAdvancements == null ? Set.of() : Set.copyOf(detaillessAdvancements);
        advancementOrders = copyIdentifierListMap(advancementOrders);
        criterionOrders = copyStringListMap(criterionOrders);
    }

    public static AdvancementOrderData empty() {
        return new AdvancementOrderData(Set.of(), List.of(), Set.of(), Set.of(), Map.of(), Map.of());
    }

    public boolean isTrackedAdvancement(Identifier id) {
        if (id == null) {
            return false;
        }

        String path = id.getPath();
        int separatorIndex = path.indexOf('/');
        if (separatorIndex <= 0) {
            return false;
        }

        Identifier rootId = Identifier.fromNamespaceAndPath(id.getNamespace(), path.substring(0, separatorIndex) + "/root");
        return this.trackedRoots.contains(rootId);
    }

    public boolean isHiddenAdvancement(Identifier id) {
        return id != null && this.hiddenAdvancements.contains(id);
    }

    public int rootOrderIndex(Identifier rootId) {
        int index = this.rootOrder.indexOf(rootId);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    public int advancementOrderIndex(Identifier advancementId) {
        for (List<Identifier> order : this.advancementOrders.values()) {
            int index = order.indexOf(advancementId);
            if (index >= 0) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    public int criterionOrderIndex(Identifier advancementId, String criterionKey) {
        List<String> order = this.criterionOrders.get(advancementId);
        if (order == null) {
            return Integer.MAX_VALUE;
        }

        int index = order.indexOf(criterionKey);
        return index >= 0 ? index : Integer.MAX_VALUE;
    }

    private static Map<Identifier, List<Identifier>> copyIdentifierListMap(Map<Identifier, List<Identifier>> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, List<Identifier>> copy = new HashMap<>();
        for (Map.Entry<Identifier, List<Identifier>> entry : input.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static Map<Identifier, List<String>> copyStringListMap(Map<Identifier, List<String>> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, List<String>> copy = new HashMap<>();
        for (Map.Entry<Identifier, List<String>> entry : input.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }
}
