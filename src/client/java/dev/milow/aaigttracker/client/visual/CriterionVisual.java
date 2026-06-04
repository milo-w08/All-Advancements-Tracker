package dev.milow.aaigttracker.client.visual;

import java.util.List;
import net.minecraft.network.chat.Component;

public record CriterionVisual(
        Component displayName,
        CriterionIcon icon,
        List<CriterionIcon> supplementaryIcons,
        SupplementaryIconMode supplementaryIconMode
) {
}
