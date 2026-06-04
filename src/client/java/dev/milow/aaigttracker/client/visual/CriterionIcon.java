package dev.milow.aaigttracker.client.visual;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record CriterionIcon(ItemStack itemStack, EntityPreviewSpec entityPreviewSpec, BiomePreviewSpec biomePreviewSpec) {
    public CriterionIcon {
        itemStack = itemStack == null ? ItemStack.EMPTY : itemStack;
    }

    public static CriterionIcon item(ItemStack itemStack) {
        return new CriterionIcon(itemStack == null ? ItemStack.EMPTY : itemStack.copy(), null, null);
    }

    public static CriterionIcon entity(ItemStack fallbackItemStack, EntityPreviewSpec entityPreviewSpec) {
        return new CriterionIcon(fallbackItemStack == null ? ItemStack.EMPTY : fallbackItemStack.copy(), entityPreviewSpec, null);
    }

    public static CriterionIcon biome(ItemStack fallbackItemStack, BiomePreviewSpec biomePreviewSpec) {
        return new CriterionIcon(fallbackItemStack == null ? ItemStack.EMPTY : fallbackItemStack.copy(), null, biomePreviewSpec);
    }

    public boolean isEntityPreview() {
        return this.entityPreviewSpec != null;
    }

    public boolean isBiomePreview() {
        return this.biomePreviewSpec != null && !this.biomePreviewSpec.isEmpty();
    }

    public boolean isItem() {
        return !this.itemStack.isEmpty();
    }

    public boolean isEmpty() {
        return !this.isEntityPreview() && !this.isBiomePreview() && !this.isItem();
    }

    public ItemStack copyItemStack() {
        return this.itemStack.copy();
    }
}
