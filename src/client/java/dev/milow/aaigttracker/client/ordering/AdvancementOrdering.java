package dev.milow.aaigttracker.client.ordering;

import dev.milow.aaigttracker.client.data.TrackerDataRepository;
import dev.milow.aaigttracker.client.model.AdvancementSnapshot;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class AdvancementOrdering {
    public static final Comparator<AdvancementSnapshot> SNAPSHOT_ORDER = Comparator
            .comparingInt((AdvancementSnapshot snapshot) -> rootOrder(snapshot.rootId()))
            .thenComparingInt(AdvancementOrdering::snapshotOrder)
            .thenComparing(snapshot -> snapshot.title().getString(), String.CASE_INSENSITIVE_ORDER);

    private AdvancementOrdering() {
    }

    public static boolean isTrackedRunAdvancement(Identifier id) {
        return id != null
                && "minecraft".equals(id.getNamespace())
                && TrackerDataRepository.current().advancements().isTrackedAdvancement(id);
    }

    public static int rootOrder(Identifier rootId) {
        return TrackerDataRepository.current().advancements().rootOrderIndex(rootId);
    }

    public static int snapshotOrder(AdvancementSnapshot snapshot) {
        int configuredOrder = TrackerDataRepository.current().advancements().advancementOrderIndex(snapshot.id());
        if (configuredOrder != Integer.MAX_VALUE) {
            return configuredOrder;
        }

        return 10_000 + snapshot.treeIndex();
    }

    public static List<String> orderCriteria(Identifier advancementId, List<String> criteria) {
        Map<String, Integer> originalIndexes = new HashMap<>();
        for (int index = 0; index < criteria.size(); index++) {
            originalIndexes.put(criteria.get(index), index);
        }

        criteria.sort(Comparator
                .comparingInt((String criterion) -> criterionOrder(advancementId, criterion))
                .thenComparingInt(criterion -> originalIndexes.getOrDefault(criterion, Integer.MAX_VALUE)));
        return List.copyOf(criteria);
    }

    private static int criterionOrder(Identifier advancementId, String criterion) {
        if (advancementId == null) {
            return Integer.MAX_VALUE;
        }

        return TrackerDataRepository.current().advancements().criterionOrderIndex(advancementId, criterion);
    }
}
