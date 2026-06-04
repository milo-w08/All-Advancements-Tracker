package dev.milow.aaigttracker.client.data;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

public record ItemObjectiveData(
        Map<Identifier, ItemCompletionGateData> gates,
        List<TrimTemplateObjectiveData> trimTemplates,
        List<PotionObjectiveData> potions
) {
    public ItemObjectiveData {
        gates = gates == null ? Map.of() : Map.copyOf(gates);
        trimTemplates = trimTemplates == null ? List.of() : List.copyOf(trimTemplates);
        potions = potions == null ? List.of() : List.copyOf(potions);
    }

    public static ItemObjectiveData empty() {
        return new ItemObjectiveData(Map.of(), List.of(), List.of());
    }

    public ItemCompletionGateData gate(Identifier id) {
        return this.gates.get(id);
    }
}
