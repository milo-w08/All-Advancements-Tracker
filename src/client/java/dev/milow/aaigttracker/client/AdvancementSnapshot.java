package dev.milow.aaigttracker.client;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record AdvancementSnapshot(
        Identifier id,
        Identifier rootId,
        Component rootTitle,
        ItemStack rootIcon,
        Component title,
        Component description,
        ItemStack icon,
        boolean done,
        float percent,
        float boardX,
        float boardY,
        int totalCriteria,
        int completedCriteria,
        List<CriterionSnapshot> criteria,
        List<RequirementGroupSnapshot> requirementGroups,
        int treeIndex,
        Long completionTimeMillis
) {
    public int remainingCriteria() {
        return Math.max(0, this.totalCriteria - this.completedCriteria);
    }

    public boolean hasKnownCompletionTime() {
        return this.completionTimeMillis != null;
    }
}
