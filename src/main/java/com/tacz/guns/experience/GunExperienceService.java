package com.tacz.guns.experience;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.nbt.GunItemDataAccessor;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageLevelUp;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Server-authoritative per-gun proficiency.
 *
 * <p>It intentionally has no relationship to industrial component condition:
 * experience follows the physical gun stack that fired a projectile, whereas
 * maintenance is a separate manufacturing/service concern. This first restored
 * version awards visible proficiency from confirmed hits and kills only; it
 * does not silently alter damage, reliability, or repair cost.</p>
 */
public final class GunExperienceService {
    public static final int MAX_LEVEL = 10;
    public static final String EXPERIENCE_TOKEN_TAG = "GunExperienceToken";

    private static final int HIT_EXPERIENCE = 8;
    private static final int KILL_EXPERIENCE = 28;
    private static final int HEADSHOT_BONUS = 4;

    private GunExperienceService() {
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
