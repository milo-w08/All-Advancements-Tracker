package dev.milow.aaigttracker.client;

import net.minecraft.resources.Identifier;

public record EntityPreviewSpec(
        Identifier entityId,
        VariantType variantType,
        Identifier variantId
) {
    public EntityPreviewSpec {
        if (variantType == null) {
            variantType = VariantType.NONE;
        }
    }

    public static EntityPreviewSpec entity(Identifier entityId) {
        return new EntityPreviewSpec(entityId, VariantType.NONE, null);
    }

    public static EntityPreviewSpec cat(Identifier variantId) {
        return new EntityPreviewSpec(Identifier.fromNamespaceAndPath("minecraft", "cat"), VariantType.CAT, variantId);
    }

    public static EntityPreviewSpec wolf(Identifier variantId) {
        return new EntityPreviewSpec(Identifier.fromNamespaceAndPath("minecraft", "wolf"), VariantType.WOLF, variantId);
    }

    public static EntityPreviewSpec frog(Identifier variantId) {
        return new EntityPreviewSpec(Identifier.fromNamespaceAndPath("minecraft", "frog"), VariantType.FROG, variantId);
    }

    public enum VariantType {
        NONE,
        CAT,
        WOLF,
        FROG
    }
}
