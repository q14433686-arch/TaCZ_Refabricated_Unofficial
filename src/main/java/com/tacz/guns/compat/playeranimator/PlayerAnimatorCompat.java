package com.tacz.guns.compat.playeranimator;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.pal.PalAnimationManager;
import com.tacz.guns.compat.playeranimator.pal.PalAssetManager;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.io.File;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/** Compatibility facade migrated from the discontinued KosmX PlayerAnimator to PAL 1.2.5. */
public final class PlayerAnimatorCompat {
    public static final Identifier LOWER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "lower_animation");
    public static final Identifier LOOP_UPPER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "loop_upper_animation");
    public static final Identifier ONCE_UPPER_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "once_upper_animation");
    public static final Identifier ROTATION_ANIMATION = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "rotation");

    private static final String PAL = "player_animation_library";
    private static boolean installed;

    private PlayerAnimatorCompat() {
    }

    public static void init() {
        installed = FabricLoader.getInstance().isModLoaded(PAL);
        if (installed) {
            PalAnimationManager.init();
        }
    }

    public static boolean loadAnimationFromZip(ZipFile zipFile, String zipPath) {
        return installed && PalAssetManager.INSTANCE.load(zipFile, zipPath);
    }

    public static void loadAnimationFromFile(File file) {
        if (installed) {
            PalAssetManager.INSTANCE.load(file);
        }
    }

    public static void clearAllAnimationCache() {
        if (installed) {
            PalAssetManager.INSTANCE.clearAll();
        }
    }

    public static boolean hasPlayerAnimator3rd(LivingEntity livingEntity, GunDisplayInstance display) {
        return installed && livingEntity instanceof AbstractClientPlayer
                && PalAnimationManager.hasAnimations(display);
    }

    public static void stopAllAnimation(LivingEntity livingEntity) {
        stopAllAnimation(livingEntity, 8);
    }

    public static void stopAllAnimation(LivingEntity livingEntity, int fadeTime) {
        if (installed && livingEntity instanceof AbstractClientPlayer player) {
            PalAnimationManager.stopAll(player, fadeTime);
        }
    }

    public static void playAnimation(LivingEntity livingEntity, GunDisplayInstance display, float limbSwingAmount) {
        if (installed && livingEntity instanceof AbstractClientPlayer player) {
            PalAnimationManager.play(player, display, limbSwingAmount);
        }
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static void registerReloadListener(Consumer<IdentifiableResourceReloadListener> register) {
        if (installed) {
            register.accept(PalAssetManager.INSTANCE);
        }
    }
}
