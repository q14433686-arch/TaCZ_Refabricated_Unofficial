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
 *
 * <p>被 {@code SoundEngineMixin} 每次计算音量时调用，
 * 因此<b>必须极轻量</b> —— 只做一次效果查询与一次乘法。
 *
 * <h2>为什么不照搬上游的做法</h2>
 * 上游 {@code SoundHandler} 的实现是：
 * <ol>
 *   <li>用<b>反射</b>抠 {@code SoundEngine} 的私有字段 {@code instanceToChannel}；</li>
 *   <li>每 tick 遍历所有正在播放的声音，逐个 {@code setVolume}；</li>
 *   <li>用一个 {@code SoundMuted} 包装类替换 {@code SoundInstance}；</li>
 *   <li>依赖 NeoForge 专有的 {@code PlaySoundEvent} / {@code PlaySoundSourceEvent}。</li>
 * </ol>
 * 这套在 26.2 + Fabric 上<b>三重不成立</b>：反射字段名可能已变、
 * 两个事件是 NeoForge 专有、且需要维护「原始音量」表以便恢复。
 *
 * <p>本移植改为在<b>音量计算的唯一收敛点</b>动手：字节码确认
 * {@code SoundEngine#calculateVolume(SoundInstance)} 内部直接转调
 * {@code calculateVolume(float, SoundSource)}，
 * 后者是所有音效音量的<b>单一出口</b>。
 * 在它的返回值上乘一个系数即可，<b>无需反射、无需包装类、无需恢复原值</b>
 * （效果结束后系数自然回到 1）。
 *
 * <p>这也顺带解决了上游要特判 {@code TickableSoundInstance} 的问题 ——
 * 我们不碰任何 {@code SoundInstance}，只改最终数值。
 */
@Environment(EnvType.CLIENT)
public final class DeafenState {
    /** 耳鸣最重时保留的音量比例（0.01 = 几乎全聋）。 */
    private static final float MIN_VOLUME_FACTOR = 0.01f;
    /** 剩余时长超过此值即为「最重」，之后线性恢复。 */
    private static final float FADE_START_TICKS = 100f;

    private DeafenState() {
    }

    /** 当前是否正在播放耳鸣声，避免重复播放。 */
    private static StunRingingSound ringing;

    /**
     * 取当前应施加的音量系数。
     *
     * @return 1.0 表示不衰减
     */
    public static float getVolumeFactor(SoundSource source) {
        // 界面音效（按钮点击等）不该被战场效果影响，否则玩家会以为游戏卡了
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
        // 剩余越久压得越狠；接近结束时线性恢复到正常
        float progress = Math.min(effect.getDuration() / FADE_START_TICKS, 1.0f);
        return MIN_VOLUME_FACTOR + (1.0f - progress) * (1.0f - MIN_VOLUME_FACTOR);
    }

    /**
     * 每 tick 驱动耳鸣声的播放与停止。
     *
     * <p>只在<b>刚获得 {@code DEAFENED} 且当前没在响</b>时启动一次；
     * 停止交给 {@link StunRingingSound#tick()} 自行判断
     * （效果消失时它会 {@code stop()}），这样淡出逻辑集中在一处。
     */
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

    /**
     * 该音效是否是我们自己的耳鸣声。
     *
     * <p><b>必须豁免</b>：耳鸣声正是在 {@code DEAFENED} 期间播放的，
     * 若被 {@code SoundEngineMixin} 一并压低，就会变成「什么都听不见」
     * 而不是「耳朵在响」—— 效果完全走样。
     */
    public static boolean isRingingSound(@org.jetbrains.annotations.Nullable
                                         net.minecraft.client.resources.sounds.SoundInstance sound) {
        return sound instanceof StunRingingSound;
    }
}
