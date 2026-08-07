package com.tacz.guns.experience;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageLevelUp;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Implementation layer for TACZ's original per-gun GunLevel contract.
 *
 * <p>This class deliberately keeps the original GunLevelExp storage and IGun level API. Experience follows the physical gun stack that fired a projectile,
 * whereas industrial maintenance remains a separate manufacturing/service
 * concern. It now grants small, server-configurable handling improvements to
 * ADS time, actual projectile spread, and local recoil camera only. It never
 * adds direct damage, armor penetration, repair discounts, or a bypass for a
 * real maintenance fault.</p>
 */
public final class GunLevelImplementation {
    public static final int MAX_LEVEL = 10;
    public static final String EXPERIENCE_TOKEN_TAG = "GunExperienceToken";

    private static final int HIT_EXPERIENCE = 8;
    private static final int KILL_EXPERIENCE = 28;
    private static final int HEADSHOT_BONUS = 4;

    private GunLevelImplementation() {
    }

    /** Minimum total experience required for a level. */
    public static int experienceForLevel(int level) {
        int safe = Math.clamp(level, 0, MAX_LEVEL);
        return safe * safe * 100;
    }

    public static int levelForExperience(int experience) {
        int safe = Math.max(0, experience);
        for (int level = MAX_LEVEL; level > 0; level--) {
            if (safe >= experienceForLevel(level)) {
                return level;
            }
        }
        return 0;
    }

    /** True when the server policy enables real proficiency handling effects. */
    public static boolean isHandlingEnabled() {
        return SyncConfig.GUN_EXPERIENCE_HANDLING_ENABLED == null
                || SyncConfig.GUN_EXPERIENCE_HANDLING_ENABLED.get();
    }

    /** Multiplier for both client and server ADS timing; lower means faster handling. */
    public static float aimTimeMultiplier(ItemStack gun) {
        return handlingMultiplier(gun, SyncConfig.GUN_EXPERIENCE_AIM_TIME_REDUCTION);
    }

    /** Server-authoritative multiplier for actual projectile inaccuracy; lower means tighter spread. */
    public static float inaccuracyMultiplier(ItemStack gun) {
        return handlingMultiplier(gun, SyncConfig.GUN_EXPERIENCE_INACCURACY_REDUCTION);
    }

    /** Client camera multiplier; it affects recoil presentation but never damage or projectile velocity. */
    public static float recoilMultiplier(ItemStack gun) {
        return handlingMultiplier(gun, SyncConfig.GUN_EXPERIENCE_RECOIL_REDUCTION);
    }

    /**
     * Player-facing evidence that experience has a real effect. This reads the
     * same synced server configuration and per-stack level used by the runtime
     * paths; it is not a second client-only progression calculation.
     */
    @Nullable
    public static Component getHandlingTooltipLine(ItemStack gun) {
        if (!isHandlingEnabled() || level(gun) <= 0) {
            return null;
        }
        int aim = Math.round((1.0F - aimTimeMultiplier(gun)) * 100.0F);
        int spread = Math.round((1.0F - inaccuracyMultiplier(gun)) * 100.0F);
        int recoil = Math.round((1.0F - recoilMultiplier(gun)) * 100.0F);
        if (aim <= 0 && spread <= 0 && recoil <= 0) {
            return null;
        }
        return Component.translatable("tooltip.tacz.gun.proficiency_handling",
                        aim + "%", spread + "%", recoil + "%")
                .withStyle(style -> style.withColor(0x8FD6C6));
    }

    private static float handlingMultiplier(ItemStack gun, net.minecraftforge.common.ForgeConfigSpec.DoubleValue maximumReduction) {
        if (!isHandlingEnabled()) {
            return 1.0F;
        }
        int level = level(gun);
        if (level <= 0) {
            return 1.0F;
        }
        double configured = maximumReduction == null ? 0.0D : maximumReduction.get();
        float reduction = Double.isFinite(configured)
                ? (float) Math.max(0.0D, Math.min(0.75D, configured)) : 0.0F;
        float progress = Math.clamp(level, 0, MAX_LEVEL) / (float) MAX_LEVEL;
        return Math.max(0.25F, 1.0F - reduction * progress);
    }

    private static int level(ItemStack gun) {
        return gun.getItem() instanceof IGun iGun ? Math.clamp(iGun.getLevel(gun), 0, MAX_LEVEL) : 0;
    }

    /**
     * Attach a stable per-stack token when a projectile is created. The token
     * lets a delayed hit award proficiency to the exact gun rather than a
     * different same-GunId stack selected after firing.
     */
    public static String captureToken(ItemStack gun) {
        if (!(gun.getItem() instanceof IGun)) {
            return "";
        }
        CompoundTag tag = ItemNbtUtils.getTag(gun);
        String token = tag.getStringOr(EXPERIENCE_TOKEN_TAG, "");
        if (!token.isBlank()) {
            return token;
        }
        String generated = UUID.randomUUID().toString();
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putString(EXPERIENCE_TOKEN_TAG, generated));
        return generated;
    }

    public static void awardHit(LivingEntity attacker, Identifier gunId, String token,
                                boolean headshot, boolean kill) {
        if (!(attacker instanceof ServerPlayer player) || gunId == null || token == null || token.isBlank()) {
            return;
        }
        int award = (kill ? KILL_EXPERIENCE : HIT_EXPERIENCE) + (headshot ? HEADSHOT_BONUS : 0);
        ItemStack gun = findMatchingGun(player, gunId, token);
        if (gun.isEmpty()) {
            return;
        }
        addExperience(player, gun, award);
    }

    private static ItemStack findMatchingGun(ServerPlayer player, Identifier gunId, String token) {
        ItemStack main = player.getMainHandItem();
        if (matches(main, gunId, token)) {
            return main;
        }
        ItemStack offhand = player.getOffhandItem();
        if (matches(offhand, gunId, token)) {
            return offhand;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getNonEquipmentItems().size(); slot++) {
            ItemStack candidate = inventory.getItem(slot);
            if (matches(candidate, gunId, token)) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean matches(ItemStack stack, Identifier gunId, String token) {
        if (!(stack.getItem() instanceof IGun gun) || !gunId.equals(gun.getGunId(stack))) {
            return false;
        }
        return token.equals(ItemNbtUtils.getTag(stack).getStringOr(EXPERIENCE_TOKEN_TAG, ""));
    }

    private static void addExperience(ServerPlayer player, ItemStack gun, int amount) {
        if (!(gun.getItem() instanceof IGun iGun) || amount <= 0) {
            return;
        }
        int previous = Math.max(0, iGun.getExp(gun));
        int oldLevel = levelForExperience(previous);
        int updated = Math.min(experienceForLevel(MAX_LEVEL), previous + amount);
        if (updated == previous) {
            return;
        }
        ItemNbtUtils.updateTag(gun, tag -> tag.putInt(GunItemDataAccessor.GUN_EXP_TAG, updated));
        int newLevel = levelForExperience(updated);
        if (newLevel > oldLevel) {
            NetworkHandler.sendToClientPlayer(new ServerMessageLevelUp(gun.copy(), newLevel), player);
        }
        player.inventoryMenu.broadcastFullState();
    }
}
