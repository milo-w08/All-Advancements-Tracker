package dev.milow.aaigttracker.client;

import java.util.List;
import net.minecraft.network.chat.Component;

public record CriterionSnapshot(
        String key,
        Component displayName,
        CriterionIcon icon,
        List<CriterionIcon> supplementaryIcons,
        SupplementaryIconMode supplementaryIconMode,
        boolean completed,
        Long completionTimeMillis,
        boolean showCompletionTooltip
) {
    public CriterionSnapshot {
        supplementaryIcons = supplementaryIcons == null ? List.of() : List.copyOf(supplementaryIcons);
        if (supplementaryIconMode == null) {
            supplementaryIconMode = supplementaryIcons.isEmpty()
                    ? SupplementaryIconMode.NONE
                    : SupplementaryIconMode.CYCLE_ONE;
        }
    }

    public boolean hasKnownCompletionTime() {
        return this.completionTimeMillis != null;
    }
}
