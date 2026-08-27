package me.xjqsh.lrtactical.client.audio;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.init.ModEffects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 耳鸣声 —— 被闪光弹震到后持续的高频蜂鸣。
 *
 * <p>与 {@code DeafenState}（压低其他声音）是<b>两件独立的事</b>，
 * 合在一起才是完整的「被震聋」：周围安静下来 + 耳朵里嗡嗡响。
 *
 * <h2>【2026-08-27 改回 {@code SoundSource.PLAYERS}】</h2>
 * 上一轮为了让消声豁免它，把它改成了 {@code SoundSource.MASTER}（配合
 * {@code DeafenState#getVolumeFactor} 对 MASTER 的整体放行）。结果<b>耳鸣声听不见了</b>，
 * 而同一份代码在 1.21.11 用 {@code PLAYERS} 是能听见的（用户实测）。
 *
 * <p>26.2 的 {@code SoundEngine#play} 里有一条<b>只在 DEBUG 级别打日志</b>的静默丢弃分支
 * （字节码 @306–@354）：算出的音量为 0 就直接 {@code return NOT_STARTED}，
 * 日志默认看不见。音量 =
 * {@code clamp(getVolume(),0,1) * clamp(options.getSoundSourceVolume(source),0,1)}，
 * 而 {@code getSoundSourceVolume} = {@code soundSourceVolumes.get(source).get()}。
 * MASTER 在那张表里的取值我没能从字节码定案 —— 但既然存在这条静默路径，
 * 就不该把耳鸣声押在 MASTER 上。</p>
 *
 * <p>现在改回 {@code PLAYERS}（与 1.21.11 一致、用户实测可闻），
 * 消声豁免改由 {@code SoundInstanceVolumeMixin} 用 {@code instanceof} 在实例层面做 ——
 * <b>不再依赖 SoundSource 是哪个类别</b>，也就不存在「改类别就把耳鸣压没」的隐雷。
 * {@code sounds.json} 里的 {@code "category": "player"} 现在与本类一致了。</p>
 *
 * <h2>26.2 移植要点</h2>
 * <ul>
 *   <li>{@code AbstractTickableSoundInstance} 的构造签名是
 *       {@code (SoundEvent, SoundSource, RandomSource)}（字节码确认），
 *       1.21.1 起就已需要 {@code RandomSource}；</li>
 *   <li>父类字段 {@code volume/pitch/looping/relative/attenuation} 均可直接赋值
 *       （{@code AbstractSoundInstance} 上为 protected）。</li>
 * </ul>
 *
 * <p>音源：用户提供的 Freesound 公开素材，已转为 OGG Vorbis
 * （MC 不接受 wav/mp3）并做等功率交叉淡化处理成可循环片段。
 */
@Environment(EnvType.CLIENT)
public class StunRingingSound extends AbstractTickableSoundInstance {
    public static final Identifier RINGING_ID =
            Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, "entity.stun_grenade.ringing");

    private static final SoundEvent RINGING = SoundEvent.createVariableRangeEvent(RINGING_ID);

    /** 剩余时长超过此值即为满音量，之后线性淡出。 */
    private static final float FADE_START_TICKS = 60f;

    public StunRingingSound() {
        super(RINGING, SoundSource.PLAYERS, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0F;
        this.pitch = 1.0F;
        // relative=true：声音跟着玩家走，不受位置与朝向影响 ——
        // 耳鸣是「在你脑袋里」，不是世界中某一点发出的
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            this.stop();
            return;
        }
        MobEffectInstance effect = player.getEffect(ModEffects.DEAFENED);
        if (effect == null) {
            this.stop();
            return;
        }
        // 快结束时淡出，避免"啪"地一下静音
        int remaining = effect.getDuration();
        this.volume = remaining >= FADE_START_TICKS ? 1.0F : remaining / FADE_START_TICKS;
    }
}
