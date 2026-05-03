package dev.milow.aaigttracker.client;

import java.util.List;
import net.minecraft.network.chat.Component;

record CriterionVisual(
        Component displayName,
        CriterionIcon icon,
        List<CriterionIcon> supplementaryIcons,
        SupplementaryIconMode supplementaryIconMode
) {
}
