package dev.milow.aaigttracker.client.data;

import dev.milow.aaigttracker.client.model.AdvancementSnapshot;
import dev.milow.aaigttracker.client.model.CriterionSnapshot;
import java.util.List;
import net.minecraft.resources.Identifier;

public final class ItemCompletionGateData {
    private final List<GateRequirementData> requirements;

    ItemCompletionGateData(List<GateRequirementData> requirements) {
        this.requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }

    public boolean isComplete(List<AdvancementSnapshot> snapshots) {
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
}
