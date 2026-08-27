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
 * <h2>【硬性约束】必须用 {@code SoundSource.MASTER}，不要改成 PLAYERS</h2>
 * {@code SoundEngineMixin} 会压低耳鸣期间的音效，而耳鸣声本身就播在这段时间里 ——
 * 不豁免就会把自己也压到几乎听不见，变成「什么都听不到」而不是「耳朵在响」。
 *
 * <p>26.2 的消声注入点是 {@code SoundEngine#calculateVolume(float, SoundSource)}
 * （内层重载；26.2 的 {@code play()} 只走它，字节码证据见 {@code SoundEngineMixin}），
 * <b>拿不到 SoundInstance</b>，所以没法再按「是不是耳鸣声」做实例级豁免 ——
 * 唯一的豁免通道就是 {@link DeafenState#getVolumeFactor} 对
 * {@code MASTER / MUSIC / UI} 三个类别的整体放行。</p>
 *
 * <p>因此：本类构造时传的 {@code SoundSource.MASTER} <b>就是豁免机制本身</b>。
 * 谁把它改成 {@code PLAYERS}（1.21.11 那条线用的正是 PLAYERS），
 * 耳鸣声就会被自己的消声压没。注意 {@code sounds.json} 里写的
 * {@code "category": "player"} 不参与音量计算 —— 26.2 的 {@code play()} 取的是
 * {@code SoundInstance#getSource()}（字节码 @177 → @189），两者不一致无害，
 * 但改这里之前请先读 {@code SoundEngineMixin}。</p>
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
        super(RINGING, SoundSource.MASTER, RandomSource.create());
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
