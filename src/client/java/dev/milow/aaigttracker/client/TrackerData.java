package dev.milow.aaigttracker.client;

import java.util.Map;
import java.util.List;
import net.minecraft.resources.Identifier;

record TrackerData(
        AdvancementOrderData advancements,
        CriteriaVisualData criteriaVisuals,
        ItemObjectiveData itemObjectives,
        Map<String, List<Identifier>> breedingItems
) {
    TrackerData {
        advancements = advancements == null ? AdvancementOrderData.empty() : advancements;
        criteriaVisuals = criteriaVisuals == null ? CriteriaVisualData.empty() : criteriaVisuals;
        itemObjectives = itemObjectives == null ? ItemObjectiveData.empty() : itemObjectives;
        breedingItems = breedingItems == null ? Map.of() : Map.copyOf(breedingItems);
    }

    static TrackerData empty() {
        return new TrackerData(
                AdvancementOrderData.empty(),
                CriteriaVisualData.empty(),
                ItemObjectiveData.empty(),
                Map.of()
        );
    }
}
