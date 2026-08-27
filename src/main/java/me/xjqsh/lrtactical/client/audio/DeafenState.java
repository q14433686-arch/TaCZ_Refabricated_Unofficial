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

    // 【2026-08-27 删除】这里曾有一个 isRingingSound(SoundInstance)：靠 instanceof +
    // 反射猜 getLocation()/toString() 里的名字来判断「这是不是耳鸣声」，以便豁免消声。
    //
    // 删掉的原因：消声的注入点已从 calculateVolume(SoundInstance) 移到
    // calculateVolume(float, SoundSource)（26.2 里 play() 只走后者，旧注入点对新播放的
    // 音效完全不生效 —— 详见 SoundEngineMixin 类注释的字节码证据），
    // 而内层重载拿不到 SoundInstance，实例级豁免已无处可用。
    //
    // 现在耳鸣声的豁免完全依赖【它用 SoundSource.MASTER 构造】+ getVolumeFactor
    // 对 MASTER/MUSIC/UI 的整体放行。这条约束写在 StunRingingSound 的类注释里。
}
