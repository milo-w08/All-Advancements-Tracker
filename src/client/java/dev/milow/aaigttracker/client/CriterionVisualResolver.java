package dev.milow.aaigttracker.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class CriterionVisualResolver {
    private static final String TWO_BY_TWO_ADVANCEMENT = "minecraft:husbandry/bred_all_animals";

    private CriterionVisualResolver() {
    }

    static CriterionVisual decorate(Identifier advancementId, String criterionKey, CriterionVisual visual) {
        if (visual == null) {
            return null;
        }

        List<CriterionIcon> supplementaryIcons = supplementaryIconsFor(advancementId, criterionKey);
        if (supplementaryIcons.isEmpty()) {
            return visual;
        }

        return new CriterionVisual(
                visual.displayName(),
                visual.icon(),
                supplementaryIcons,
                SupplementaryIconMode.CYCLE_ONE
        );
    }

    private static List<CriterionIcon> supplementaryIconsFor(Identifier advancementId, String criterionKey) {
        if (advancementId == null || criterionKey == null || !TWO_BY_TWO_ADVANCEMENT.equals(advancementId.toString())) {
            return List.of();
        }

        List<Identifier> itemIds = TrackerDataRepository.current().breedingItems().get(stripNamespace(criterionKey));
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of();
        }

        List<CriterionIcon> icons = new ArrayList<>(itemIds.size());
        for (Identifier itemId : itemIds) {
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null || item == Items.AIR) {
                continue;
            }

            icons.add(CriterionIcon.item(item.getDefaultInstance()));
        }
        return List.copyOf(icons);
    }

    private static String stripNamespace(String rawKey) {
        int separatorIndex = rawKey.indexOf(':');
        return separatorIndex >= 0 ? rawKey.substring(separatorIndex + 1) : rawKey;
    }
}
