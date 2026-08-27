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
            // 26.2 的 SoundManager#play 有返回值，而它的失败路径里有几条【几乎不留痕】的
            // 分支：音量算出 0 时只在 DEBUG 级别打一行；找不到音效定义时那条 WARN 带 Marker，
            // 某些日志配置下也看不到。耳鸣声听不见时默认日志里可能什么都没有 ——
            // 前两轮就是因此排查了很久。所以这里把结果接住，非 STARTED 就 WARN 一次
            // （只一次，避免每 tick 刷屏），并把已知的几个坑直接写进消息里。
            var result = mc.getSoundManager().play(ringing);
            if (result != net.minecraft.client.sounds.SoundEngine.PlayResult.STARTED
                    && !playFailureWarned) {
                playFailureWarned = true;
                me.xjqsh.lrtactical.EquipmentMod.LOGGER.warn(
                        "[LRTactical] Stun ringing sound did not start: result={} id={}. "
                                + "排查顺序：① assets/lrtactical/sounds.json 顶层是否混入了"
                                + "非对象值（例如 _comment 字符串 —— 26.2 按 "
                                + "Map<String,SoundEventRegistration> 整体反序列化，"
                                + "一个坏键会让整个文件作废）；② sounds/stun_ringing.ogg 是否存在；"
                                + "③ '{}' 音量滑条是否为 0。可跑 scripts/verify_lr_assets.py 自查。",
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
