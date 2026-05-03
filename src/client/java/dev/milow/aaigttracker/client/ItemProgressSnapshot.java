package dev.milow.aaigttracker.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record ItemProgressSnapshot(
        Identifier id,
        Component title,
        Component description,
        ItemStack icon,
        int current,
        int total,
        boolean done,
        List<CriterionSnapshot> criteria
) {
    public ItemProgressSnapshot {
        icon = icon == null ? ItemStack.EMPTY : icon.copy();
        criteria = criteria == null ? List.of() : List.copyOf(criteria);
    }

    public float percent() {
        if (this.total <= 0) {
            return this.done ? 1.0F : 0.0F;
        }

        return Math.min(1.0F, this.current / (float) this.total);
    }
}
