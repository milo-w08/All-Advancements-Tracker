package dev.milow.aaigttracker.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

final class ItemProgressService {
    private static final Identifier SMITHING_WITH_STYLE_ADVANCEMENT_ID =
            Identifier.parse("minecraft:adventure/trim_with_all_exclusive_armor_patterns");
    private final ItemScanner itemScanner = new ItemScanner();

    List<ItemProgressSnapshot> scanItemObjectives(List<AdvancementSnapshot> advancementSnapshots) {
        ItemScanResult scanResult = this.itemScanner.scan();

        List<ItemProgressSnapshot> items = new ArrayList<>(12);
        items.add(this.createEnderChestProgress(scanResult));
        items.add(this.createConduitProgress(scanResult));
        items.add(this.createBeaconProgress(scanResult));
        items.add(this.createNetheriteProgress(scanResult));
        items.add(this.createOminousBottlesProgress(scanResult));
        items.add(this.createEndCrystalProgress(scanResult));
        items.add(this.createSmithingWithStyleProgress(scanResult, advancementSnapshots));
        items.add(this.createSingleItemProgress(
                Identifier.fromNamespaceAndPath("aaigttracker", "enchanted_golden_apple"),
                Component.translatable("screen.aaigttracker.items.ega.title"),
                Component.translatable("screen.aaigttracker.items.ega.description"),
                Items.ENCHANTED_GOLDEN_APPLE.getDefaultInstance(),
                scanResult,
                Items.ENCHANTED_GOLDEN_APPLE,
                1
        ));
        items.add(this.createSingleItemProgress(
                Identifier.fromNamespaceAndPath("aaigttracker", "sniffer_eggs"),
                Component.translatable("screen.aaigttracker.items.sniffer_eggs.title"),
                Component.translatable("screen.aaigttracker.items.sniffer_eggs.description"),
                Items.SNIFFER_EGG.getDefaultInstance(),
                scanResult,
                Items.SNIFFER_EGG,
                2
        ));
        items.add(this.createTridentProgress(scanResult));
        items.add(this.createPiercingCrossbowProgress(scanResult));
        items.add(this.createPotionProgress(scanResult));
        return List.copyOf(items);
    }

    private ItemProgressSnapshot createEnderChestProgress(ItemScanResult scanResult) {
        int enderChestCount = scanResult.itemCount(Items.ENDER_CHEST);
        int obsidianCount = scanResult.itemCount(Items.OBSIDIAN);
        int eyeCount = scanResult.itemCount(Items.ENDER_EYE);
        boolean hasEnderChest = enderChestCount >= 1;
        boolean hasMaterials = obsidianCount >= 8 && eyeCount >= 1;
        boolean done = hasEnderChest || hasMaterials;
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("ender_chest", Items.ENDER_CHEST, enderChestCount, 1, hasEnderChest),
                this.countedItemCriterion("ender_chest_obsidian", Items.OBSIDIAN, obsidianCount, 8, done || obsidianCount >= 8),
                this.countedItemCriterion("ender_chest_eye", Items.ENDER_EYE, eyeCount, 1, done || eyeCount >= 1)
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "ender_chest"),
                Component.translatable("screen.aaigttracker.items.ender_chest.title"),
                Component.translatable("screen.aaigttracker.items.ender_chest.description"),
                Items.ENDER_CHEST.getDefaultInstance(),
                done ? 9 : Math.min(8, obsidianCount) + Math.min(1, eyeCount),
                9,
                done,
                criteria
        );
    }

    private ItemProgressSnapshot createOminousBottlesProgress(ItemScanResult scanResult) {
        int bottleCount = scanResult.itemCount(Items.OMINOUS_BOTTLE);
        int activeCredit = this.activeOminousBottleCredit();
        int bottlesNeeded = Math.max(0, 4 - activeCredit);
        CriterionSnapshot criterion = this.countedItemCriterion(
                "ominous_bottle",
                Items.OMINOUS_BOTTLE.getDefaultInstance(),
                Component.literal(Items.OMINOUS_BOTTLE.getName(Items.OMINOUS_BOTTLE.getDefaultInstance()).getString()
                        + " " + bottleCount + "/" + bottlesNeeded),
                bottleCount,
                bottlesNeeded
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "ominous_bottles"),
                Component.translatable("screen.aaigttracker.items.ominous_bottles.title"),
                Component.translatable("screen.aaigttracker.items.ominous_bottles.description"),
                Items.OMINOUS_BOTTLE.getDefaultInstance(),
                Math.min(4, bottleCount + activeCredit),
                4,
                bottleCount >= bottlesNeeded,
                List.of(criterion)
        );
    }

    private int activeOminousBottleCredit() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return 0;
        }

        int credit = 0;
        if (minecraft.player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) {
            credit++;
        }
        if (minecraft.player.hasEffect(MobEffects.TRIAL_OMEN)) {
            credit++;
        }
        return credit;
    }

    private ItemProgressSnapshot createTridentProgress(ItemScanResult scanResult) {
        int tridentCount = scanResult.itemCount(Items.TRIDENT);
        int channelingTridentCount = scanResult.channelingTridentCount();
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("trident", Items.TRIDENT, tridentCount, 1),
                this.countedItemCriterion(
                        "channeling_trident",
                        Items.ENCHANTED_BOOK.getDefaultInstance(),
                        Component.literal("Channeling Trident " + channelingTridentCount + "/1"),
                        channelingTridentCount,
                        1
                )
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "trident"),
                Component.translatable("screen.aaigttracker.items.trident.title"),
                Component.translatable("screen.aaigttracker.items.trident.description"),
                Items.TRIDENT.getDefaultInstance(),
                Math.min(1, tridentCount) + Math.min(1, channelingTridentCount),
                2,
                tridentCount >= 1 && channelingTridentCount >= 1,
                criteria
        );
    }

    private ItemProgressSnapshot createPiercingCrossbowProgress(ItemScanResult scanResult) {
        int crossbowCount = scanResult.itemCount(Items.CROSSBOW);
        int piercingCrossbowCount = scanResult.piercingCrossbowCount();
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("crossbow", Items.CROSSBOW, crossbowCount, 1),
                this.countedItemCriterion(
                        "piercing_crossbow",
                        Items.ENCHANTED_BOOK.getDefaultInstance(),
                        Component.literal("Piercing IV Crossbow " + piercingCrossbowCount + "/1"),
                        piercingCrossbowCount,
                        1
                )
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "piercing_crossbow"),
                Component.translatable("screen.aaigttracker.items.piercing_crossbow.title"),
                Component.translatable("screen.aaigttracker.items.piercing_crossbow.description"),
                Items.CROSSBOW.getDefaultInstance(),
                Math.min(1, crossbowCount) + Math.min(1, piercingCrossbowCount),
                2,
                crossbowCount >= 1 && piercingCrossbowCount >= 1,
                criteria
        );
    }

    private ItemProgressSnapshot createEndCrystalProgress(ItemScanResult scanResult) {
        int crystalCount = scanResult.itemCount(Items.END_CRYSTAL);
        int remainingCrystals = Math.max(0, 4 - crystalCount);
        int glassTarget = remainingCrystals * 7;
        int eyeTarget = remainingCrystals;
        int tearTarget = remainingCrystals;
        int glassCount = scanResult.itemCount(Items.GLASS);
        int eyeCount = scanResult.itemCount(Items.ENDER_EYE);
        int tearCount = scanResult.itemCount(Items.GHAST_TEAR);
        int craftableCrystals = remainingCrystals == 0
                ? 0
                : Math.min(glassCount / 7, Math.min(eyeCount, tearCount));
        int availableCrystals = Math.min(4, crystalCount + craftableCrystals);
        boolean done = availableCrystals >= 4;
        List<CriterionSnapshot> criteria = new ArrayList<>(4);
        criteria.add(this.countedItemCriterion("end_crystal", Items.END_CRYSTAL, crystalCount, 4, crystalCount >= 4));
        if (remainingCrystals > 0) {
            criteria.add(this.countedItemCriterion("end_crystal_glass", Items.GLASS, glassCount, glassTarget, done || glassCount >= glassTarget));
            criteria.add(this.countedItemCriterion("end_crystal_eye", Items.ENDER_EYE, eyeCount, eyeTarget, done || eyeCount >= eyeTarget));
            criteria.add(this.countedItemCriterion("end_crystal_tear", Items.GHAST_TEAR, tearCount, tearTarget, done || tearCount >= tearTarget));
        }
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "end_crystals"),
                Component.translatable("screen.aaigttracker.items.end_crystals.title"),
                Component.translatable("screen.aaigttracker.items.end_crystals.description"),
                Items.END_CRYSTAL.getDefaultInstance(),
                availableCrystals,
                4,
                done,
                List.copyOf(criteria)
        );
    }

    private ItemProgressSnapshot createSmithingWithStyleProgress(ItemScanResult scanResult, List<AdvancementSnapshot> advancementSnapshots) {
        List<TrimTemplateObjectiveData> templates = TrackerDataRepository.current().itemObjectives().trimTemplates();
        List<CriterionSnapshot> criteria = new ArrayList<>(templates.size());
        int completed = 0;
        ItemStack icon = Items.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE.getDefaultInstance();
        for (TrimTemplateObjectiveData objective : templates) {
            Item item = BuiltInRegistries.ITEM.getOptional(objective.itemId()).orElse(Items.AIR);
            if (item == Items.AIR) {
                continue;
            }
            int count = scanResult.itemCount(item);
            String criterionKey = trimCriterionKey(objective.pattern());
            boolean crafted = this.isAdvancementCriterionComplete(advancementSnapshots, SMITHING_WITH_STYLE_ADVANCEMENT_ID, criterionKey);
            boolean done = crafted || count >= 1;
            if (done) {
                completed++;
            }

            ItemStack stack = item.getDefaultInstance();
            if (criteria.isEmpty()) {
                icon = stack.copy();
            }
            Component label = Component.literal(item.getName(stack).getString()
                    + (crafted ? " done" : " " + count + "/1"));
            criteria.add(this.countedItemCriterion(criterionKey, stack, label, done));
        }

        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "smithing_with_style"),
                Component.translatable("screen.aaigttracker.items.smithing_with_style.title"),
                Component.translatable("screen.aaigttracker.items.smithing_with_style.description"),
                icon,
                completed,
                templates.size(),
                !templates.isEmpty() && completed >= templates.size(),
                List.copyOf(criteria)
        );
    }

    private ItemProgressSnapshot createConduitProgress(ItemScanResult scanResult) {
        int heartCount = scanResult.itemCount(Items.HEART_OF_THE_SEA);
        int shellCount = scanResult.itemCount(Items.NAUTILUS_SHELL);
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("heart_of_the_sea", Items.HEART_OF_THE_SEA, heartCount, 1),
                this.countedItemCriterion("nautilus_shell", Items.NAUTILUS_SHELL, shellCount, 8)
        );
        int current = Math.min(1, heartCount) + Math.min(8, shellCount);
        int total = 9;
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "conduit"),
                Component.translatable("screen.aaigttracker.items.conduit.title"),
                Component.translatable("screen.aaigttracker.items.conduit.description"),
                Items.CONDUIT.getDefaultInstance(),
                current,
                total,
                heartCount >= 1 && shellCount >= 8,
                criteria
        );
    }

    private ItemProgressSnapshot createBeaconProgress(ItemScanResult scanResult) {
        int skullCount = scanResult.itemCount(Items.WITHER_SKELETON_SKULL);
        int goldBlockCount = scanResult.itemCount(Items.GOLD_BLOCK);
        List<CriterionSnapshot> criteria = List.of(
                this.countedItemCriterion("wither_skeleton_skull", Items.WITHER_SKELETON_SKULL, skullCount, 3),
                this.countedItemCriterion("gold_block", Items.GOLD_BLOCK, goldBlockCount, 164)
        );
        int current = Math.min(3, skullCount) + Math.min(164, goldBlockCount);
        int total = 167;
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "beacon"),
                Component.translatable("screen.aaigttracker.items.beacon.title"),
                Component.translatable("screen.aaigttracker.items.beacon.description"),
                Items.BEACON.getDefaultInstance(),
                current,
                total,
                skullCount >= 3 && goldBlockCount >= 164,
                criteria
        );
    }

    private ItemProgressSnapshot createNetheriteProgress(ItemScanResult scanResult) {
        int debrisCount = scanResult.itemCount(Items.ANCIENT_DEBRIS);
        int scrapCount = scanResult.itemCount(Items.NETHERITE_SCRAP);
        int ingotCount = scanResult.itemCount(Items.NETHERITE_INGOT);
        int debrisEquivalent = debrisCount + scrapCount + ingotCount * 4;
        List<CriterionSnapshot> criteria = List.of(
                this.infoCriterion(
                        "ancient_debris",
                        Items.ANCIENT_DEBRIS.getDefaultInstance(),
                        Component.literal(Items.ANCIENT_DEBRIS.getName(Items.ANCIENT_DEBRIS.getDefaultInstance()).getString() + ": " + debrisCount)
                ),
                this.infoCriterion(
                        "netherite_scrap",
                        Items.NETHERITE_SCRAP.getDefaultInstance(),
                        Component.literal(Items.NETHERITE_SCRAP.getName(Items.NETHERITE_SCRAP.getDefaultInstance()).getString() + ": " + scrapCount)
                ),
                this.infoCriterion(
                        "netherite_ingot",
                        Items.NETHERITE_INGOT.getDefaultInstance(),
                        Component.literal(Items.NETHERITE_INGOT.getName(Items.NETHERITE_INGOT.getDefaultInstance()).getString() + ": " + ingotCount + " (" + (ingotCount * 4) + ")")
                )
        );
        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "netherite"),
                Component.translatable("screen.aaigttracker.items.netherite.title"),
                Component.translatable("screen.aaigttracker.items.netherite.description"),
                Items.NETHERITE_INGOT.getDefaultInstance(),
                Math.min(20, debrisEquivalent),
                20,
                debrisEquivalent >= 20,
                criteria
        );
    }

    private ItemProgressSnapshot createSingleItemProgress(
            Identifier id,
            Component title,
            Component description,
            ItemStack icon,
            ItemScanResult scanResult,
            Item targetItem,
            int targetCount
    ) {
        int count = scanResult.itemCount(targetItem);
        CriterionSnapshot criterion = this.countedItemCriterion(stripNamespace(id.toString()), targetItem, count, targetCount);
        return new ItemProgressSnapshot(
                id,
                title,
                description,
                icon,
                Math.min(targetCount, count),
                targetCount,
                count >= targetCount,
                List.of(criterion)
        );
    }

    private ItemProgressSnapshot createPotionProgress(ItemScanResult scanResult) {
        List<PotionObjectiveData> rows = TrackerDataRepository.current().itemObjectives().potions();
        List<CriterionSnapshot> criteria = new ArrayList<>(rows.size());
        int completed = 0;
        for (PotionObjectiveData row : rows) {
            Holder.Reference<Potion> potion = BuiltInRegistries.POTION.get(row.potionId()).orElse(null);
            if (potion == null) {
                continue;
            }

            int count = scanResult.potionCount(BuiltInRegistries.POTION.getKey(potion.value()));
            boolean done = count > 0;
            if (done) {
                completed++;
            }
            ItemStack icon = PotionContents.createItemStack(Items.POTION, potion);
            Component label = icon.getHoverName();
            List<CriterionIcon> recipeIcons = new ArrayList<>(row.ingredientIds().size());
            for (Identifier ingredientId : row.ingredientIds()) {
                Item ingredient = BuiltInRegistries.ITEM.getOptional(ingredientId).orElse(Items.AIR);
                if (ingredient != Items.AIR) {
                    recipeIcons.add(CriterionIcon.item(ingredient.getDefaultInstance()));
                }
            }
            criteria.add(new CriterionSnapshot(
                    row.key(),
                    label,
                    CriterionIcon.item(icon),
                    recipeIcons,
                    SupplementaryIconMode.STATIC_ALL,
                    done,
                    null,
                    false
            ));
        }

        return new ItemProgressSnapshot(
                Identifier.fromNamespaceAndPath("aaigttracker", "potions"),
                Component.translatable("screen.aaigttracker.items.potions.title"),
                Component.translatable("screen.aaigttracker.items.potions.description"),
                Items.POTION.getDefaultInstance(),
                completed,
                rows.size(),
                !rows.isEmpty() && completed >= rows.size(),
                List.copyOf(criteria)
        );
    }

    private CriterionSnapshot countedItemCriterion(String key, Item item, int current, int target) {
        return this.countedItemCriterion(key, item, current, target, current >= target);
    }

    private CriterionSnapshot countedItemCriterion(String key, Item item, int current, int target, boolean completed) {
        ItemStack stack = item.getDefaultInstance();
        Component label = Component.literal(item.getName(stack).getString() + " " + current + "/" + target);
        return this.countedItemCriterion(key, stack, label, completed);
    }

    private CriterionSnapshot countedItemCriterion(String key, ItemStack stack, Component label, int current, int target) {
        return this.countedItemCriterion(key, stack, label, current >= target);
    }

    private CriterionSnapshot countedItemCriterion(String key, ItemStack stack, Component label, boolean completed) {
        return new CriterionSnapshot(
                key,
                label,
                CriterionIcon.item(stack),
                List.of(),
                SupplementaryIconMode.NONE,
                completed,
                null,
                false
        );
    }

    private CriterionSnapshot infoCriterion(String key, ItemStack icon, Component label) {
        return new CriterionSnapshot(
                key,
                label,
                CriterionIcon.item(icon),
                List.of(),
                SupplementaryIconMode.NONE,
                false,
                null,
                false
        );
    }

    private boolean isAdvancementCriterionComplete(
            List<AdvancementSnapshot> advancementSnapshots,
            Identifier advancementId,
            String criterionKey
    ) {
        if (advancementId == null || criterionKey == null) {
            return false;
        }

        for (AdvancementSnapshot snapshot : advancementSnapshots) {
            if (!snapshot.id().equals(advancementId)) {
                continue;
            }
            for (CriterionSnapshot criterion : snapshot.criteria()) {
                String key = criterion.key();
                if ((criterionKey.equals(key) || criterionKey.equals(stripNamespace(key))) && criterion.completed()) {
                    return true;
                }
            }
            return snapshot.done();
        }
        return false;
    }

    private static String trimCriterionKey(String pattern) {
        return "armor_trimmed_minecraft:" + pattern + "_armor_trim_smithing_template_smithing_trim";
    }

    private static String stripNamespace(String value) {
        if (value == null) {
            return "";
        }

        int separatorIndex = value.indexOf(':');
        return separatorIndex >= 0 && separatorIndex + 1 < value.length()
                ? value.substring(separatorIndex + 1)
                : value;
    }

}
