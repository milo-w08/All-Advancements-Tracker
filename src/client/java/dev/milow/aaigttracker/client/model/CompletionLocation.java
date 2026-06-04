package dev.milow.aaigttracker.client.model;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public record CompletionLocation(Identifier dimensionId, int x, int y, int z) {
    public static CompletionLocation fromPlayer(LocalPlayer player) {
        if (player == null || player.level() == null) {
            return null;
        }

        Identifier dimensionId = player.level().dimension().identifier();
        return new CompletionLocation(
                dimensionId,
                player.blockPosition().getX(),
                player.blockPosition().getY(),
                player.blockPosition().getZ()
        );
    }

    public Component dimensionName() {
        if (Identifier.fromNamespaceAndPath("minecraft", "overworld").equals(this.dimensionId)) {
            return Component.literal("Overworld");
        }
        if (Identifier.fromNamespaceAndPath("minecraft", "the_nether").equals(this.dimensionId)) {
            return Component.literal("Nether");
        }
        if (Identifier.fromNamespaceAndPath("minecraft", "the_end").equals(this.dimensionId)) {
            return Component.literal("End");
        }

        String path = this.dimensionId == null ? "Unknown" : this.dimensionId.getPath().replace('_', ' ');
        return Component.literal(path);
    }

    public String compactText() {
        return this.dimensionName().getString() + " " + this.x + " " + this.y + " " + this.z;
    }
}
