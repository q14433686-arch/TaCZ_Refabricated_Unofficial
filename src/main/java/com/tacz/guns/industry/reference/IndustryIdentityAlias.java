package com.tacz.guns.industry.reference;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Explicit repair for an upstream table recipe whose result id is stale or
 * typoed. Aliases are deliberately opt-in data: TACZ may suggest a candidate
 * in its audit, but it never rewrites an ambiguous third-party gun id based on
 * a filename guess.
 */
public final class IndustryIdentityAlias {
    private static final Set<String> KINDS = Set.of("gun", "ammo", "attachment");
    private static final Set<String> CONFIDENCE = Set.of("curated", "pack_declared", "world_confirmed");

    /** The canonical TableRecipe id, e.g. rainforest:gun/56. */
    @SerializedName("recipe")
    private Identifier recipe;

    @SerializedName("kind")
    private String kind = "";

    /** The real loaded gun/ammo/attachment identity to use for this recipe. */
    @SerializedName("target")
    private Identifier target;

    /** Optional guard for gun aliases, so a later pack revision cannot be silently remapped. */
    @SerializedName("expected_ammo")
    private Identifier expectedAmmo;

    @SerializedName("expected_capacity")
    private int expectedCapacity;

    /** Optional guard for ammo aliases against a changed stack-size contract. */
    @SerializedName("expected_stack_size")
    private int expectedStackSize;

    @SerializedName("confidence")
    private String confidence = "curated";

    @SerializedName("reason")
    private String reason = "";

    @Nullable
    public Identifier getRecipe() {
        return recipe;
    }

    public String getKind() {
        return kind == null ? "" : kind;
    }

    @Nullable
    public Identifier getTarget() {
        return target;
    }

    @Nullable
    public Identifier getExpectedAmmo() {
        return expectedAmmo;
    }

    public int getExpectedCapacity() {
        return expectedCapacity;
    }

    public int getExpectedStackSize() {
        return expectedStackSize;
    }

    public String getConfidence() {
        return confidence == null ? "" : confidence;
    }

    public String getReason() {
        return reason == null ? "" : reason;
    }

    public Validation validateDeclaration() {
        if (recipe == null || target == null || !KINDS.contains(getKind())) {
            return Validation.invalid("recipe, target and supported kind are required");
        }
        if (!CONFIDENCE.contains(getConfidence()) || getReason().isBlank()) {
            return Validation.invalid("explicit alias requires supported confidence and non-empty reason");
        }
        if (expectedCapacity < 0 || expectedStackSize < 0) {
            return Validation.invalid("expected capacity/stack size cannot be negative");
        }
        return Validation.valid();
    }

    public Validation validateAgainst(ICommonResourceProvider assets) {
        Validation declaration = validateDeclaration();
        if (!declaration.valid()) {
            return declaration;
        }
        if (!targetIsLoaded(assets)) {
            return Validation.invalid("target identity is not loaded: " + target);
        }
        return switch (getKind()) {
            case "gun" -> validateGun(assets.getGunIndex(target));
            case "ammo" -> validateAmmo(assets.getAmmoIndex(target));
            case "attachment" -> Validation.valid();
            default -> Validation.invalid("unsupported alias kind");
        };
    }

    /** Optional compatibility aliases remain dormant until their target pack is installed. */
    public boolean targetIsLoaded(ICommonResourceProvider assets) {
        if (assets == null || target == null) {
            return false;
        }
        return switch (getKind()) {
            case "gun" -> assets.getGunIndex(target) != null;
            case "ammo" -> assets.getAmmoIndex(target) != null;
            case "attachment" -> assets.getAttachmentIndex(target) != null;
            default -> false;
        };
    }

    private Validation validateAmmo(@Nullable CommonAmmoIndex index) {
        if (index == null) {
            return Validation.invalid("target ammo index is not loaded: " + target);
        }
        if (expectedStackSize < 0) {
            return Validation.invalid("expected_stack_size cannot be negative");
        }
        if (expectedStackSize > 0 && expectedStackSize != index.getStackSize()) {
            return Validation.invalid("expected_stack_size disagrees with target ammo index stack_size");
        }
        return Validation.valid();
    }

    private Validation validateGun(@Nullable CommonGunIndex index) {
        if (index == null || index.getGunData() == null) {
            return Validation.invalid("target gun index is not loaded: " + target);
        }
        if (expectedAmmo != null && !expectedAmmo.equals(index.getGunData().getAmmoId())) {
            return Validation.invalid("expected_ammo disagrees with target GunData ammo");
        }
        if (expectedCapacity > 0 && expectedCapacity != index.getGunData().getAmmoAmount()) {
            return Validation.invalid("expected_capacity disagrees with target GunData ammo_amount");
        }
        if (expectedCapacity < 0) {
            return Validation.invalid("expected_capacity cannot be negative");
        }
        return Validation.valid();
    }

    public record Validation(boolean valid, String reason) {
        public static Validation valid() {
            return new Validation(true, "");
        }

        public static Validation invalid(String reason) {
            return new Validation(false, reason == null ? "invalid alias" : reason);
        }
    }
}
