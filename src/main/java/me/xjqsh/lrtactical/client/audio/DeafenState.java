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
    /** 只在第一次播放失败时告警，之后闭嘴（否则会每个 tick 刷一行）。 */
    private static boolean playFailureWarned;

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
            // 26.2 的 SoundManager#play 有返回值，而且失败路径里有一条【只在 DEBUG 级别
            // 打日志】的静默丢弃（音量算出 0 就直接 NOT_STARTED）。耳鸣声听不见时
            // 默认日志里什么都看不到 —— 上一轮就是因此排查了很久。
            // 所以这里把结果接住，非 STARTED 就 WARN 一次（只一次，避免刷屏）。
            var result = mc.getSoundManager().play(ringing);
            if (result != net.minecraft.client.sounds.SoundEngine.PlayResult.STARTED
                    && !playFailureWarned) {
                playFailureWarned = true;
                me.xjqsh.lrtactical.EquipmentMod.LOGGER.warn(
                        "[LRTactical] Stun ringing sound did not start: result={} id={} "
                                + "(check assets/lrtactical/sounds.json + sounds/stun_ringing.ogg "
                                + "and the '{}' volume slider)",
                        result, StunRingingSound.RINGING_ID,
                        net.minecraft.sounds.SoundSource.PLAYERS.getName());
            }
        }
    }

    // 【2026-08-27 删除】这里曾有一个 isRingingSound(SoundInstance)：靠 instanceof +
    // 反射猜 getLocation()/toString() 里的名字来判断「这是不是耳鸣声」，以便豁免消声。
    //
    // 现在不需要它了：消声注入点是 AbstractSoundInstance#getVolume()
    // （见 SoundInstanceVolumeMixin 的类注释，里面有三次搬迁的完整字节码证据），
    // 那里 this 就是音效实例，直接 instanceof StunRingingSound 即可豁免 ——
    // 既不用反射猜名字，也不依赖耳鸣声用哪个 SoundSource。
}
