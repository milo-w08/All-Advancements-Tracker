package dev.milow.aaigttracker.client.visual;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record BiomePreviewSpec(Identifier biomeId, List<ItemStack> itemStacks) {
    public BiomePreviewSpec {
        if (itemStacks == null || itemStacks.isEmpty()) {
            itemStacks = List.of();
        } else {
            List<ItemStack> copies = new ArrayList<>(itemStacks.size());
            for (ItemStack stack : itemStacks) {
                if (stack != null && !stack.isEmpty()) {
                    copies.add(stack.copy());
                }
            }
            itemStacks = List.copyOf(copies);
        }
    }

    public boolean isEmpty() {
        return this.biomeId == null;
    }

    public List<ItemStack> copyItemStacks() {
        List<ItemStack> copies = new ArrayList<>(this.itemStacks.size());
        for (ItemStack stack : this.itemStacks) {
            copies.add(stack.copy());
        }
        return List.copyOf(copies);
    }
}
