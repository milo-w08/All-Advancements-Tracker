package dev.milow.aaigttracker.client;

import java.util.List;
import net.minecraft.resources.Identifier;

record ItemCompletionGateData(List<GateRequirementData> requirements) {
    ItemCompletionGateData {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }

    boolean isComplete(List<AdvancementSnapshot> snapshots) {
        if (this.requirements.isEmpty()) {
            return false;
        }

        for (GateRequirementData requirement : this.requirements) {
            if (!requirement.isComplete(snapshots)) {
                return false;
            }
        }
        return true;
    }
}

record GateRequirementData(Identifier advancementId, String criterionKey) {
    boolean isComplete(List<AdvancementSnapshot> snapshots) {
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

    private static String stripNamespace(String value) {
        if (value == null) {
            return "";
        }

        int separatorIndex = value.indexOf(':');
        return separatorIndex >= 0 && separatorIndex + 1 < value.length()
                ? value.substring(separatorIndex + 1)
                : value;
    }
}

record TrimTemplateObjectiveData(String pattern, Identifier itemId) {
}

record PotionObjectiveData(String key, Identifier potionId, List<Identifier> ingredientIds) {
    PotionObjectiveData {
        ingredientIds = ingredientIds == null ? List.of() : List.copyOf(ingredientIds);
    }
}
