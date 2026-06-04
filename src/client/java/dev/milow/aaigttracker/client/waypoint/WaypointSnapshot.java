package dev.milow.aaigttracker.client.waypoint;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record WaypointSnapshot(
        Identifier id,
        String label,
        Identifier dimensionId,
        int x,
        int y,
        int z,
        long createdAtMillis
) {
    public Component title() {
        return Component.literal(this.label == null || this.label.isBlank() ? "Waypoint" : this.label);
    }

    public Component description() {
        return Component.literal(this.positionText());
    }

    public ItemStack icon() {
        if (this.dimensionId == null) {
            return Items.COMPASS.getDefaultInstance();
        }

        return switch (this.dimensionId.toString()) {
            case "minecraft:the_nether" -> Items.CLOCK.getDefaultInstance();
            case "minecraft:the_end" -> Items.RECOVERY_COMPASS.getDefaultInstance();
            default -> Items.COMPASS.getDefaultInstance();
        };
    }

    public String positionText() {
        return this.dimensionName().getString() + " " + this.x + " " + this.y + " " + this.z;
    }

    public Component dimensionName() {
        if (this.dimensionId == null) {
            return Component.literal("Unknown");
        }

        return switch (this.dimensionId.toString()) {
            case "minecraft:overworld" -> Component.literal("Overworld");
            case "minecraft:the_nether" -> Component.literal("Nether");
            case "minecraft:the_end" -> Component.literal("End");
            default -> Component.literal(humanize(this.dimensionId.getPath()));
        };
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }

        String[] words = value.replace('/', ' ').replace('_', ' ').split(" +");
        StringBuilder builder = new StringBuilder(value.length());
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                builder.append(word.substring(1));
            }
        }
        return builder.isEmpty() ? value : builder.toString();
    }
}
