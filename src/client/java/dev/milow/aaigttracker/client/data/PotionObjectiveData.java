package dev.milow.aaigttracker.client.data;

import java.util.List;
import net.minecraft.resources.Identifier;

public record PotionObjectiveData(String key, Identifier potionId, List<Identifier> ingredientIds) {
    public PotionObjectiveData {
        ingredientIds = ingredientIds == null ? List.of() : List.copyOf(ingredientIds);
    }
}
