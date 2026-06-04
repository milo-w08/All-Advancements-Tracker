package dev.milow.aaigttracker.client.item;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

final class ItemScanResult {
    private final Map<Item, Integer> itemCounts = new HashMap<>();
    private final Map<Identifier, Integer> potionCounts = new HashMap<>();
    private int channelingTridentCount;
    private int piercingCrossbowCount;

    void addItem(Item item, int amount) {
        if (item == null || amount <= 0) {
            return;
        }
        this.itemCounts.merge(item, amount, Integer::sum);
    }

    void addPotion(Identifier potionId, int amount) {
        if (potionId == null || amount <= 0) {
            return;
        }
        this.potionCounts.merge(potionId, amount, Integer::sum);
    }

    void addChannelingTridents(int amount) {
        if (amount > 0) {
            this.channelingTridentCount += amount;
        }
    }

    void addPiercingCrossbows(int amount) {
        if (amount > 0) {
            this.piercingCrossbowCount += amount;
        }
    }

    int itemCount(Item item) {
        return this.itemCounts.getOrDefault(item, 0);
    }

    int potionCount(Identifier potionId) {
        return this.potionCounts.getOrDefault(potionId, 0);
    }

    int channelingTridentCount() {
        return this.channelingTridentCount;
    }

    int piercingCrossbowCount() {
        return this.piercingCrossbowCount;
    }
}
