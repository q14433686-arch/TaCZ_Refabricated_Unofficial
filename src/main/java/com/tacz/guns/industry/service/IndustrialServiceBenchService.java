package com.tacz.guns.industry.service;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.industry.item.IndustryItemBuilder;
import com.tacz.guns.industry.maintenance.IndustryMaintenanceService;
import com.tacz.guns.industry.magazine.EnBlocClipService;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side transactional validation for the industrial service bench.
 *
 * <p>This is deliberately a real multi-slot component transaction, not a
 * repair button on a complete gun. Disassembly preserves five individually
 * identified components; reassembly requires the same GunId/platform, a
 * production template, a verified fitting fixture, and an actual armorer
 * wrench. No input is consumed until the block entity has validated the whole
 * operation and reserved every destination slot.</p>
 */
public final class IndustrialServiceBenchService {
    public static final List<String> COMPONENT_ORDER = List.of("receiver", "bolt", "barrel", "trigger", "recoil");
    public static final String PART_CONDITION = "IndustryPartCondition";
    public static final String SERVICE_GUN_ID = "IndustryServiceGunId";
    public static final String SERVICE_ORIGIN = "IndustryServiceOrigin";
    public static final String SERVICE_RECIPE = "IndustryServiceRecipe";
    public static final String SERVICE_TIER = "IndustryServiceTier";
    public static final String SERVICE_ACTION = "IndustryServiceAction";
    public static final String SERVICE_SCOPE = "IndustryServiceToolingScope";
    public static final String SERVICE_SHOTS = "IndustryServiceShots";
    public static final int WRENCH_MAX_DAMAGE = 256;

    private IndustrialServiceBenchService() {
    }

    public static DisassemblyPlan planDisassembly(ItemStack gunStack, ItemStack blueprint, ItemStack fixture,
                                                  ItemStack wrench) {
        if (!(gunStack.getItem() instanceof IGun gun)) {
            return DisassemblyPlan.failure(Failure.INVALID_GUN);
        }
        if (!isGunSafeToService(gunStack, gun)) {
            return DisassemblyPlan.failure(Failure.GUN_NOT_STRIPPED);
        }
        Origin origin = origin(gunStack, gun);
        if (origin == null) {
            return DisassemblyPlan.failure(Failure.GUN_NOT_INDUSTRIAL);
        }
        if (!matchesBlueprint(blueprint, origin)) {
            return DisassemblyPlan.failure(Failure.BLUEPRINT_MISMATCH);
        }
        if (!matchesFixture(fixture, origin)) {
            return DisassemblyPlan.failure(Failure.FIXTURE_MISMATCH);
        }
        if (!isUsableWrench(wrench)) {
            return DisassemblyPlan.failure(Failure.WRENCH_REQUIRED);
        }

        IndustryMaintenanceService.Snapshot maintenance = IndustryMaintenanceService.getSnapshot(gunStack);
        List<ItemStack> components = new ArrayList<>();
        for (String kind : COMPONENT_ORDER) {
            int condition = conditionFor(maintenance, kind);
            ItemStack component = IndustryItemBuilder.component()
                    .platform(origin.platform())
                    .kind(kind)
                    .displayNameKey("item.tacz.gun_component.service_" + kind)
                    .actionProfile(origin.action())
                    .toolingScope(origin.scope())
                    .build();
            ItemNbtUtils.updateTag(component, tag -> writeServiceIdentity(tag, origin, kind, condition, maintenance.shots()));
            components.add(component);
        }
        return DisassemblyPlan.success(origin, components);
    }

    public static ReassemblyPlan planReassembly(List<ItemStack> components, ItemStack blueprint, ItemStack fixture,
                                                ItemStack wrench) {
        if (components == null || components.size() != COMPONENT_ORDER.size()) {
            return ReassemblyPlan.failure(Failure.COMPONENT_SET_INVALID);
        }
        Origin origin = null;
        Set<String> foundKinds = new HashSet<>();
        int receiver = IndustryMaintenanceService.MAX_CONDITION;
        int bolt = IndustryMaintenanceService.MAX_CONDITION;
        int barrel = IndustryMaintenanceService.MAX_CONDITION;
        int trigger = IndustryMaintenanceService.MAX_CONDITION;
        int recoil = IndustryMaintenanceService.MAX_CONDITION;
        long shots = 0L;
        for (ItemStack component : components) {
            if (!component.is(ModItems.GUN_COMPONENT)) {
                return ReassemblyPlan.failure(Failure.COMPONENT_SET_INVALID);
            }
            CompoundTag tag = ItemNbtUtils.getTag(component);
            Origin componentOrigin = originFromComponent(tag);
            String kind = tag.getStringOr("IndustryPartKind", "");
            if (componentOrigin == null || !COMPONENT_ORDER.contains(kind) || !foundKinds.add(kind)) {
                return ReassemblyPlan.failure(Failure.COMPONENT_SET_INVALID);
            }
            if (origin == null) {
                origin = componentOrigin;
            } else if (!origin.equals(componentOrigin)) {
                return ReassemblyPlan.failure(Failure.COMPONENT_SET_INVALID);
            }
            int condition = Math.clamp(tag.getIntOr(PART_CONDITION, IndustryMaintenanceService.MAX_CONDITION),
                    0, IndustryMaintenanceService.MAX_CONDITION);
            switch (kind) {
                case "receiver" -> receiver = condition;
                case "bolt" -> bolt = condition;
                case "barrel" -> barrel = condition;
                case "trigger" -> trigger = condition;
                case "recoil" -> recoil = condition;
                default -> {
                }
            }
            shots = Math.max(shots, Math.max(0L, tag.getLongOr(SERVICE_SHOTS, 0L)));
        }
        if (origin == null || foundKinds.size() != COMPONENT_ORDER.size()) {
            return ReassemblyPlan.failure(Failure.COMPONENT_SET_INVALID);
        }
        if (!matchesBlueprint(blueprint, origin)) {
            return ReassemblyPlan.failure(Failure.BLUEPRINT_MISMATCH);
        }
        if (!matchesFixture(fixture, origin)) {
            return ReassemblyPlan.failure(Failure.FIXTURE_MISMATCH);
        }
        if (!isUsableWrench(wrench)) {
            return ReassemblyPlan.failure(Failure.WRENCH_REQUIRED);
        }
        FireMode fireMode = TimelessAPI.getCommonGunIndex(origin.gunId())
                .map(index -> index.getGunData().getFireModeSet())
                .filter(modes -> !modes.isEmpty())
                .map(modes -> modes.getFirst())
                .orElse(FireMode.UNKNOWN);
        ItemStack gun = GunItemBuilder.create()
                .setId(origin.gunId())
                .setFireMode(fireMode)
                .setAmmoCount(0)
                .setAmmoInBarrel(false)
                .build();
        if (gun.isEmpty()) {
            return ReassemblyPlan.failure(Failure.INVALID_GUN);
        }
        ItemNbtUtils.updateTag(gun, tag -> {
            tag.putString(IndustryMaintenanceService.ASSEMBLY_PLATFORM_TAG, origin.platform());
            tag.putString(IndustryMaintenanceService.ASSEMBLY_RECIPE_TAG, origin.recipe());
            tag.putString(IndustryMaintenanceService.ASSEMBLY_TIER_TAG, origin.tier());
            tag.putString(IndustryMaintenanceService.ASSEMBLY_ACTION_TAG, origin.action());
            tag.putString(IndustryMaintenanceService.ASSEMBLY_TOOLING_SCOPE_TAG, origin.scope());
            tag.putInt(IndustryMaintenanceService.SCHEMA_TAG, IndustryMaintenanceService.SCHEMA_VERSION);
            tag.putInt(IndustryMaintenanceService.RECEIVER_TAG, receiver);
            tag.putInt(IndustryMaintenanceService.BOLT_TAG, bolt);
            tag.putInt(IndustryMaintenanceService.BARREL_TAG, barrel);
            tag.putInt(IndustryMaintenanceService.TRIGGER_TAG, trigger);
            tag.putInt(IndustryMaintenanceService.RECOIL_TAG, recoil);
            tag.putInt(IndustryMaintenanceService.FOULING_TAG, 0);
            tag.putLong(IndustryMaintenanceService.SHOTS_TAG, shots);
            tag.remove(IndustryMaintenanceService.SEED_TAG);
        });
        return ReassemblyPlan.success(origin, gun);
    }

    /** Apply native 26.2 durability only after a complete successful transaction. */
    public static void damageWrench(ItemStack wrench) {
        if (!wrench.is(ModItems.ARMORER_WRENCH)) {
            return;
        }
        ensureWrenchComponents(wrench);
        Integer current = wrench.get(DataComponents.DAMAGE);
        int damage = current == null ? 0 : Math.max(0, current);
        if (damage + 1 >= WRENCH_MAX_DAMAGE) {
            wrench.shrink(1);
        } else {
            wrench.set(DataComponents.DAMAGE, damage + 1);
        }
    }

    public static boolean isUsableWrench(ItemStack wrench) {
        if (!wrench.is(ModItems.ARMORER_WRENCH)) {
            return false;
        }
        ensureWrenchComponents(wrench);
        Integer damage = wrench.get(DataComponents.DAMAGE);
        return (damage == null ? 0 : damage) < WRENCH_MAX_DAMAGE;
    }

    private static void ensureWrenchComponents(ItemStack wrench) {
        if (wrench.get(DataComponents.MAX_DAMAGE) == null) {
            wrench.set(DataComponents.MAX_DAMAGE, WRENCH_MAX_DAMAGE);
        }
        if (wrench.get(DataComponents.DAMAGE) == null) {
            wrench.set(DataComponents.DAMAGE, 0);
        }
    }

    private static boolean isGunSafeToService(ItemStack gunStack, IGun gun) {
        if (gun.hasInstalledMagazine(gunStack) || !EnBlocClipService.getInstalledClip(gunStack).isEmpty()) {
            return false;
        }
        if (gun.getCurrentAmmoCount(gunStack) > 0 || gun.hasBulletInBarrel(gunStack)) {
            return false;
        }
        for (AttachmentType type : AttachmentType.values()) {
            if (type != AttachmentType.NONE && !gun.getAttachment(gunStack, type).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static Origin origin(ItemStack gunStack, IGun gun) {
        CompoundTag tag = ItemNbtUtils.getTag(gunStack);
        String platform = tag.getStringOr(IndustryMaintenanceService.ASSEMBLY_PLATFORM_TAG, "");
        String recipe = tag.getStringOr(IndustryMaintenanceService.ASSEMBLY_RECIPE_TAG, "");
        String tier = tag.getStringOr(IndustryMaintenanceService.ASSEMBLY_TIER_TAG, "");
        String action = tag.getStringOr(IndustryMaintenanceService.ASSEMBLY_ACTION_TAG, "");
        String scope = tag.getStringOr(IndustryMaintenanceService.ASSEMBLY_TOOLING_SCOPE_TAG, "");
        Identifier gunId = gun.getGunId(gunStack);
        if (platform.isBlank() || recipe.isBlank() || tier.isBlank() || action.isBlank() || scope.isBlank() || gunId == null) {
            return null;
        }
        return new Origin(gunId, platform, recipe, tier, action, scope);
    }

    @Nullable
    private static Origin originFromComponent(CompoundTag tag) {
        Identifier gunId = Identifier.tryParse(tag.getStringOr(SERVICE_GUN_ID, ""));
        String platform = tag.getStringOr(SERVICE_ORIGIN, "");
        String recipe = tag.getStringOr(SERVICE_RECIPE, "");
        String tier = tag.getStringOr(SERVICE_TIER, "");
        String action = tag.getStringOr(SERVICE_ACTION, "");
        String scope = tag.getStringOr(SERVICE_SCOPE, "");
        if (gunId == null || platform.isBlank() || recipe.isBlank() || tier.isBlank() || action.isBlank() || scope.isBlank()) {
            return null;
        }
        return new Origin(gunId, platform, recipe, tier, action, scope);
    }

    private static boolean matchesBlueprint(ItemStack blueprint, Origin origin) {
        if (!blueprint.is(ModItems.GUN_BLUEPRINT)) {
            return false;
        }
        CompoundTag tag = ItemNbtUtils.getTag(blueprint);
        return origin.platform().equals(tag.getStringOr("IndustryPlatform", ""))
                && "production".equals(tag.getStringOr("IndustryBlueprintRole", ""));
    }

    private static boolean matchesFixture(ItemStack fixture, Origin origin) {
        if (!fixture.is(ModItems.PRESS_DIE)) {
            return false;
        }
        CompoundTag tag = ItemNbtUtils.getTag(fixture);
        String kind = tag.getStringOr("IndustryPartKind", "");
        if (origin.platform().startsWith("surveyed/")) {
            return "survey_fixture".equals(kind) && "surveying".equals(tag.getStringOr("IndustryPlatform", ""));
        }
        if (!origin.action().equals(tag.getStringOr("IndustryActionProfile", ""))) {
            return false;
        }
        if ("family_jig".equals(origin.scope()) || "platform_tooling".equals(origin.scope())) {
            return "action_jig".equals(kind);
        }
        return origin.platform().equals(tag.getStringOr("IndustryPlatform", ""))
                && origin.scope().equals(tag.getStringOr("IndustryToolingScope", ""))
                && (kind.contains("gauge") || kind.contains("fixture"));
    }

    private static void writeServiceIdentity(CompoundTag tag, Origin origin, String kind, int condition, long shots) {
        tag.putInt(PART_CONDITION, Math.clamp(condition, 0, IndustryMaintenanceService.MAX_CONDITION));
        tag.putString(SERVICE_GUN_ID, origin.gunId().toString());
        tag.putString(SERVICE_ORIGIN, origin.platform());
        tag.putString(SERVICE_RECIPE, origin.recipe());
        tag.putString(SERVICE_TIER, origin.tier());
        tag.putString(SERVICE_ACTION, origin.action());
        tag.putString(SERVICE_SCOPE, origin.scope());
        tag.putLong(SERVICE_SHOTS, Math.max(0L, shots));
        tag.putString("IndustryPartKind", kind);
    }

    private static int conditionFor(IndustryMaintenanceService.Snapshot snapshot, String kind) {
        return switch (kind) {
            case "receiver" -> snapshot.receiver();
            case "bolt" -> snapshot.bolt();
            case "barrel" -> snapshot.barrel();
            case "trigger" -> snapshot.trigger();
            case "recoil" -> snapshot.recoil();
            default -> IndustryMaintenanceService.MAX_CONDITION;
        };
    }

    public enum Failure {
        INVALID_GUN("message.tacz.industrial_service.invalid_gun"),
        GUN_NOT_INDUSTRIAL("message.tacz.industrial_service.gun_not_industrial"),
        GUN_NOT_STRIPPED("message.tacz.industrial_service.gun_not_stripped"),
        BLUEPRINT_MISMATCH("message.tacz.industrial_service.blueprint_mismatch"),
        FIXTURE_MISMATCH("message.tacz.industrial_service.fixture_mismatch"),
        WRENCH_REQUIRED("message.tacz.industrial_service.wrench_required"),
        COMPONENT_SET_INVALID("message.tacz.industrial_service.component_set_invalid"),
        OUTPUT_BLOCKED("message.tacz.industrial_service.output_blocked");

        private final String key;
        Failure(String key) { this.key = key; }
        public String key() { return key; }
    }

    public record Origin(Identifier gunId, String platform, String recipe, String tier, String action, String scope) {}
    public record DisassemblyPlan(@Nullable Origin origin, List<ItemStack> components, @Nullable Failure failure) {
        static DisassemblyPlan success(Origin origin, List<ItemStack> components) { return new DisassemblyPlan(origin, List.copyOf(components), null); }
        static DisassemblyPlan failure(Failure failure) { return new DisassemblyPlan(null, List.of(), failure); }
        public boolean success() { return failure == null && components.size() == COMPONENT_ORDER.size(); }
    }
    public record ReassemblyPlan(@Nullable Origin origin, ItemStack gun, @Nullable Failure failure) {
        static ReassemblyPlan success(Origin origin, ItemStack gun) { return new ReassemblyPlan(origin, gun.copy(), null); }
        static ReassemblyPlan failure(Failure failure) { return new ReassemblyPlan(null, ItemStack.EMPTY, failure); }
        public boolean success() { return failure == null && !gun.isEmpty(); }
    }
}
