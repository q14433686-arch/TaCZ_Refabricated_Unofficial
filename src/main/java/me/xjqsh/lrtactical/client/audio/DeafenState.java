package me.xjqsh.lrtactical.client.audio;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.init.ModEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;

/** 「耳鸣时压低环境音 + 驱动耳鸣声播放」的客户端状态计算。 */
@Environment(EnvType.CLIENT)
public final class DeafenState {
    private static final float MIN_VOLUME_FACTOR = 0.01f;
    private static final float FADE_START_TICKS = 100f;

    private static StunRingingSound ringing;
    private static boolean playFailureWarned;

    private DeafenState() {
    }

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
            SoundEngine.PlayResult result = mc.getSoundManager().play(ringing);
            if (result != SoundEngine.PlayResult.STARTED && !playFailureWarned) {
                playFailureWarned = true;
                EquipmentMod.LOGGER.warn(
                        "[LRTactical] Stun ringing sound did not start: result={} id={}. "
                                + "排查顺序：① assets/lrtactical/sounds.json 顶层是否混入了非对象值；"
                                + "② sounds/stun_ringing.ogg 是否存在；③ '{}' 音量滑条是否为 0。"
                                + "可跑 scripts/verify_lr_assets.py 自查。",
                        result, StunRingingSound.RINGING_ID, SoundSource.PLAYERS.getName());
            }
        }
    }
}
