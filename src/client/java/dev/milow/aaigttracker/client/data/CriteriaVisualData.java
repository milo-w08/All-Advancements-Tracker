package dev.milow.aaigttracker.client.data;

import java.util.Map;
import net.minecraft.network.chat.Component;

public record CriteriaVisualData(
        Map<String, Component> catSources,
        Map<String, Component> wolfSources,
        Map<String, Component> entitySources,
        Map<String, Component> trimSources,
        Map<String, Component> itemInfoSources
) {
    public CriteriaVisualData {
        catSources = catSources == null ? Map.of() : Map.copyOf(catSources);
        wolfSources = wolfSources == null ? Map.of() : Map.copyOf(wolfSources);
        entitySources = entitySources == null ? Map.of() : Map.copyOf(entitySources);
        trimSources = trimSources == null ? Map.of() : Map.copyOf(trimSources);
        itemInfoSources = itemInfoSources == null ? Map.of() : Map.copyOf(itemInfoSources);
    }

    public static CriteriaVisualData empty() {
        return new CriteriaVisualData(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}
