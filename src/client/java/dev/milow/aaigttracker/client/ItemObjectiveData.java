package dev.milow.aaigttracker.client;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

record ItemObjectiveData(
        Map<Identifier, ItemCompletionGateData> gates,
        List<TrimTemplateObjectiveData> trimTemplates,
        List<PotionObjectiveData> potions
) {
    ItemObjectiveData {
        gates = gates == null ? Map.of() : Map.copyOf(gates);
        trimTemplates = trimTemplates == null ? List.of() : List.copyOf(trimTemplates);
        potions = potions == null ? List.of() : List.copyOf(potions);
    }

    static ItemObjectiveData empty() {
        return new ItemObjectiveData(Map.of(), List.of(), List.of());
    }

    ItemCompletionGateData gate(Identifier id) {
        return this.gates.get(id);
    }
}
