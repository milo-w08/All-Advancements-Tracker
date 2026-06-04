package dev.milow.aaigttracker.client.model;

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
        Long completionTimeMillis,
        CompletionLocation completionLocation
) {
    public int remainingCriteria() {
        return Math.max(0, this.totalCriteria - this.completedCriteria);
    }

    public boolean hasKnownCompletionTime() {
        return this.completionTimeMillis != null;
    }

    public AdvancementSnapshot withCompletionLocation(CompletionLocation location) {
        return new AdvancementSnapshot(
                this.id,
                this.rootId,
                this.rootTitle,
                this.rootIcon,
                this.title,
                this.description,
                this.icon,
                this.done,
                this.percent,
                this.boardX,
                this.boardY,
                this.totalCriteria,
                this.completedCriteria,
                this.criteria,
                this.requirementGroups,
                this.treeIndex,
                this.completionTimeMillis,
                location
        );
    }
}
