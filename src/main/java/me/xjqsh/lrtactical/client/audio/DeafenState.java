package me.xjqsh.lrtactical.client.audio;

import me.xjqsh.lrtactical.init.ModEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 「耳鸣时把所有音量压低」的判定与系数计算。
 */
@Environment(EnvType.CLIENT)
public final class DeafenState {
    private static final float MIN_VOLUME_FACTOR = 0.01f;
    private static final float FADE_START_TICKS = 100f;

    private DeafenState() {
    }

    private static StunRingingSound ringing;

    public static float getVolumeFactor(SoundSource source) {
        if (source == SoundSource.MASTER || source == SoundSource.MUSIC || source == SoundSource.UI) {
            return 1.0f;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return 1.0f;
        }
        MobEffectInstance effect = player.getEffect(ModEffects.DEAFENED);
        if (effect == null) {
            return 1.0f;
        }
        float progress = Math.min(effect.getDuration() / FADE_START_TICKS, 1.0f);
        return MIN_VOLUME_FACTOR + (1.0f - progress) * (1.0f - MIN_VOLUME_FACTOR);
    }

    public static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || player.getEffect(ModEffects.DEAFENED) == null) {
            ringing = null;
            return;
        }
        if (ringing == null || !mc.getSoundManager().isActive(ringing)) {
            ringing = new StunRingingSound();
            mc.getSoundManager().play(ringing);
        }
    }

    public static boolean isRingingSound(@org.jetbrains.annotations.Nullable
                                         net.minecraft.client.resources.sounds.SoundInstance sound) {
        if (sound == null) return false;
        if (sound instanceof StunRingingSound) {
            return true;
        }
        try {
            var locMethod = sound.getClass().getMethod("getLocation");
            Object loc = locMethod.invoke(sound);
            if (loc != null) {
                String s = loc.toString();
                if (s.contains("ringing") || s.contains("stun_grenade")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        try {
            String ts = sound.toString();
            if (ts != null && (ts.contains("ringing") || ts.contains("stun_grenade.ringing"))) {
                return true;
            }
        } catch (Throwable ignored) {}
        String cn = sound.getClass().getName();
        return cn.contains("StunRinging") || cn.contains("Ringing");
    }
}
