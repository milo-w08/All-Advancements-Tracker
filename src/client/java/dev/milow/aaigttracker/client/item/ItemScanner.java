package dev.milow.aaigttracker.client.item;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

final class ItemScanner {
    private static final int ITEM_SCAN_DEPTH_LIMIT = 8;

    ItemScanResult scan() {
        ItemScanResult scanResult = new ItemScanResult();
        Minecraft minecraft = Minecraft.getInstance();
        this.scanAvailablePlayerStorage(minecraft, scanResult);
        return scanResult;
    }

    private void scanAvailablePlayerStorage(Minecraft minecraft, ItemScanResult scanResult) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }

        if (this.scanServerPlayerStorage(minecraft, scanResult)) {
            return;
        }

        this.scanContainer(minecraft.player.getInventory(), scanResult);
        this.scanContainer(minecraft.player.getEnderChestInventory(), scanResult);
    }

    private boolean scanServerPlayerStorage(Minecraft minecraft, ItemScanResult scanResult) {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            return false;
        }

        UUID playerId = minecraft.player.getUUID();
        try {
            List<ItemStack> stacks = server.submit(() -> {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(playerId);
                if (serverPlayer == null) {
                    return List.<ItemStack>of();
                }

                List<ItemStack> copiedStacks = new ArrayList<>();
                copyContainerStacks(serverPlayer.getInventory(), copiedStacks);
                copyContainerStacks(serverPlayer.getEnderChestInventory(), copiedStacks);
                return List.copyOf(copiedStacks);
            }).join();
            if (stacks.isEmpty()) {
                return false;
            }
            for (ItemStack stack : stacks) {
                this.scanItemStack(stack, scanResult, 0);
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void copyContainerStacks(Container container, List<ItemStack> stacks) {
        if (container == null) {
            return;
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
    }

    private void scanContainer(Container container, ItemScanResult scanResult) {
        if (container == null) {
            return;
        }

        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            this.scanItemStack(container.getItem(slot), scanResult, 0);
        }
    }

    private void scanItemStack(ItemStack stack, ItemScanResult scanResult, int depth) {
        if (stack == null || stack.isEmpty() || depth > ITEM_SCAN_DEPTH_LIMIT) {
            return;
        }

        scanResult.addItem(stack.getItem(), stack.getCount());
        if (stack.getItem() == Items.TRIDENT && this.hasEnchantmentLevel(stack, Enchantments.CHANNELING, 1)) {
            scanResult.addChannelingTridents(stack.getCount());
        }
        if (stack.getItem() == Items.CROSSBOW && this.hasEnchantmentLevel(stack, Enchantments.PIERCING, 4)) {
            scanResult.addPiercingCrossbows(stack.getCount());
        }

        if (stack.getItem() == Items.POTION
                || stack.getItem() == Items.SPLASH_POTION
                || stack.getItem() == Items.LINGERING_POTION) {
            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            if (potionContents != null) {
                potionContents.potion().ifPresent(holder -> scanResult.addPotion(
                        BuiltInRegistries.POTION.getKey(holder.value()),
                        stack.getCount()
                ));
            }
        }

        BundleContents bundleContents = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundleContents != null) {
            bundleContents.itemCopyStream().forEach(item -> this.scanItemStack(item, scanResult, depth + 1));
        }

        ItemContainerContents containerContents = stack.get(DataComponents.CONTAINER);
        if (containerContents != null) {
            containerContents.nonEmptyItemCopyStream().forEach(item -> this.scanItemStack(item, scanResult, depth + 1));
        }
    }

    private boolean hasEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantmentKey, int minimumLevel) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments == null || enchantments.isEmpty()) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return false;
        }

        Holder.Reference<Enchantment> enchantment = minecraft.level
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(enchantmentKey)
                .orElse(null);
        return enchantment != null && enchantments.getLevel(enchantment) >= minimumLevel;
    }
}
