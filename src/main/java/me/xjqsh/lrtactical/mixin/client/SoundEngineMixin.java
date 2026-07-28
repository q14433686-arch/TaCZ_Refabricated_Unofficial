package me.xjqsh.lrtactical.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.xjqsh.lrtactical.client.audio.DeafenState;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 闪光弹的「耳鸣消声」—— 在音量计算的唯一出口处打折。
 *
 * <h2>为什么注入这里</h2>
 * 字节码确认 {@code SoundEngine} 有两个 {@code calculateVolume} 重载，
 * 且 {@code calculateVolume(SoundInstance)} 内部<b>直接转调</b>
 * {@code calculateVolume(float, SoundSource)}：
 * <pre>
 * calculateVolume(SoundInstance):
 *     getVolume() / getSource() -&gt; calculateVolume(float, SoundSource)
 * </pre>
 * 也就是说后者是<b>所有音效音量的单一收敛点</b>，
 * 在它的返回值上乘一个系数，就等于压低了全部声音。
 *
 * <h2>相比上游方案的优势</h2>
 * 上游用反射抠 {@code SoundEngine} 私有字段 + 每 tick 遍历所有播放中的声音
 * 逐个 {@code setVolume}，还要维护「原始音量表」以便恢复，
 * 并依赖两个 NeoForge 专有事件。本方案：
 * <ul>
 *   <li><b>无反射</b> —— 不怕字段改名；</li>
 *   <li><b>无需恢复</b> —— 效果结束后系数自然回到 1；</li>
 *   <li><b>无需特判 {@code TickableSoundInstance}</b> —— 不碰任何 SoundInstance 对象；</li>
 *   <li>只有一个注入点，且目标是<b>具名方法</b>而非 lambda
 *       （本仓库那个未启用的 {@code SoundEngineMixin} 就是因为依赖
 *       lambda 名而脆弱，见其类注释）。</li>
 * </ul>
 *
 * <p><b>为什么用 {@code @ModifyReturnValue} 而不是 {@code @Inject}</b>：
 * 前者语义就是「改返回值」，不需要 {@code CallbackInfoReturnable} 样板，
 * 且能与其他模组的同类注入自然叠加。本仓库已有多处先例
 * （{@code LootTableMixin} / {@code LivingEntityMixin}）。
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    /**
     * 注入<b>带 {@code SoundInstance} 的那个重载</b>，而不是它转调的
     * {@code calculateVolume(float, SoundSource)}。
     *
     * <p>原因：耳鸣声本身必须<b>豁免</b>消声（否则会把自己也压没，
     * 变成「什么都听不见」而非「耳朵在响」），
     * 而判断「是不是耳鸣声」需要拿到 {@code SoundInstance} ——
     * 内层那个重载只有 {@code float} 和 {@code SoundSource}，信息不足。
     *
     * <p>这仍然是单一收敛点：字节码确认
     * {@code calculateVolume(SoundInstance)} 的实现就是
     * {@code calculateVolume(sound.getVolume(), sound.getSource())}，
     * 所有走音量计算的声音都会经过它。
     */
    @ModifyReturnValue(
            method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
            at = @At("RETURN"))
    private float lrtactical$applyDeafen(float original, SoundInstance sound) {
        if (DeafenState.isRingingSound(sound)) {
            return original;
        }
        return original * DeafenState.getVolumeFactor(sound.getSource());
    }
}
