package com.tacz.guns.experience;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * @deprecated Use {@link GunLevelImplementation}. This forwarding facade keeps
 * older integrations source/binary-compatible while all TACZ runtime code uses
 * the original GunLevel implementation layer directly.
 */
@Deprecated(forRemoval = false)
public final class GunExperienceService {
    public static final int MAX_LEVEL = GunLevelImplementation.MAX_LEVEL;
    public static final String EXPERIENCE_TOKEN_TAG = GunLevelImplementation.EXPERIENCE_TOKEN_TAG;

    private GunExperienceService() {
    }

    public static int experienceForLevel(int level) {
        return GunLevelImplementation.experienceForLevel(level);
    }

    public static int levelForExperience(int experience) {
        return GunLevelImplementation.levelForExperience(experience);
    }

    public static boolean isHandlingEnabled() {
        return GunLevelImplementation.isHandlingEnabled();
    }

    public static float aimTimeMultiplier(ItemStack gun) {
        return GunLevelImplementation.aimTimeMultiplier(gun);
    }

    public static float inaccuracyMultiplier(ItemStack gun) {
        return GunLevelImplementation.inaccuracyMultiplier(gun);
    }

    public static float recoilMultiplier(ItemStack gun) {
        return GunLevelImplementation.recoilMultiplier(gun);
    }

    @Nullable
    public static Component getHandlingTooltipLine(ItemStack gun) {
        return GunLevelImplementation.getHandlingTooltipLine(gun);
    }

    public static String captureToken(ItemStack gun) {
        return GunLevelImplementation.captureToken(gun);
    }

    public static void awardHit(LivingEntity attacker, Identifier gunId, String token,
                                boolean headshot, boolean kill) {
        GunLevelImplementation.awardHit(attacker, gunId, token, headshot, kill);
    }
}
