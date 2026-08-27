package me.xjqsh.lrtactical.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.xjqsh.lrtactical.client.audio.DeafenState;
import me.xjqsh.lrtactical.client.audio.StunRingingSound;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 闪光弹的「耳鸣消声」—— 在音效<b>自报音量</b>的唯一出口处打折。
 *
 * <h2>注入点的两次搬迁（都留有字节码证据，别再来回改）</h2>
 *
 * <p><b>第 1 版（错误）</b>：注入 {@code SoundEngine#calculateVolume(SoundInstance)}。
 * 26.2 里 {@code play()} <b>不经过</b>它（只有 {@code tickInGameSound} 与
 * {@code refreshCategoryVolume} 经过），所以耳鸣期间新响的声音一点没被压低 ——
 * 表现为「有时闷有时不闷」。</p>
 *
 * <p><b>第 2 版（只修对了一半）</b>：改注入内层 {@code calculateVolume(float, SoundSource)}。
 * 消声确实生效了（用户实测确认），但内层<b>拿不到 {@code SoundInstance}</b>，
 * 耳鸣声只能靠「用 {@code SoundSource.MASTER} 构造 + MASTER 整体放行」来豁免 ——
 * 结果耳鸣声变成听不见。26.2 的 {@code play()} 里有一条<b>只在 DEBUG 级别打日志</b>的
 * 静默丢弃分支：
 * <pre>
 * @306  if (volume &gt; 0) goto 355
 * @310  SoundInstance.canStartSilent()
 * @320  SoundSource.MUSIC
 * @338  LOGGER.debug("Skipped playing sound {}, volume was zero.")
 * @351  return NOT_STARTED          ← 音量算出 0 就静默不播，日志默认看不见
 * </pre>
 * 音量 = {@code clamp(getVolume(),0,1) * clamp(options.getSoundSourceVolume(source),0,1)}
 * （{@code calculateVolume(F,SoundSource)} 的实现），而
 * {@code Options#getSoundSourceVolume} = {@code soundSourceVolumes.get(source).get()}。
 * MASTER 在这张表里是什么值我没能从字节码定案 —— 但既然「静默丢弃」这条路存在，
 * 就<b>不该把耳鸣声押在 MASTER 上</b>。</p>
 *
 * <p><b>第 3 版（本版）</b>：注入 {@code AbstractSoundInstance#getVolume()}。
 * 理由（26.2 jar 逐条核对）：</p>
 * <ul>
 *   <li><b>覆盖完整</b>：{@code play()} 在 @154 取 {@code getVolume()} 再交给
 *       @189 的 {@code calculateVolume(F,SoundSource)}；
 *       {@code calculateVolume(SoundInstance)} 的实现也就是
 *       {@code calculateVolume(getVolume(), getSource())}。
 *       也就是说三条路径（新播放 / tick 更新 / 改滑条）<b>全都</b>读这个方法，
 *       一处即全覆盖，且<b>不会</b>像「内外两层都挂」那样把系数乘两次。</li>
 *   <li><b>拿得到实例</b>：{@code this} 就是音效实例，耳鸣声可以用
 *       {@code instanceof} 干净地豁免 —— 不需要第 1 版那种反射猜名字的
 *       {@code isRingingSound}，也不依赖 {@code SoundSource} 是哪个类别。</li>
 * </ul>
 *
 * <h2>已知边界</h2>
 * 只覆盖 {@code AbstractSoundInstance} 的子类（原版与绝大多数模组都是）。
 * 若有模组直接实现 {@code SoundInstance} 接口而不继承它，那些音效不会被消声 ——
 * 可接受的降级，且不会影响耳鸣声本身。
 */
@Mixin(AbstractSoundInstance.class)
public class SoundInstanceVolumeMixin {

    /**
     * 耳鸣声豁免 + 其余音效按 {@link DeafenState#getVolumeFactor} 打折。
     *
     * <p>豁免必须排在最前：耳鸣声正是在 {@code DEAFENED} 生效期间播放的，
     * 不豁免就会把自己压到几乎听不见，变成「什么都听不到」而不是「耳朵在响」。</p>
     */
    @ModifyReturnValue(method = "getVolume()F", at = @At("RETURN"))
    private float lrtactical$applyDeafen(float original) {
        if ((Object) this instanceof StunRingingSound) {
            return original;
        }
        return original * DeafenState.getVolumeFactor(((SoundInstance) (Object) this).getSource());
    }
}
