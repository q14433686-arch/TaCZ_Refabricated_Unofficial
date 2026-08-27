package me.xjqsh.lrtactical.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.xjqsh.lrtactical.client.audio.DeafenState;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 闪光弹的「耳鸣消声」—— 在音量计算的唯一出口处打折。
 *
 * <h2>【2026-08-27 修复】注入点从 {@code calculateVolume(SoundInstance)}
 * 换成了 {@code calculateVolume(float, SoundSource)}</h2>
 *
 * 本类此前注入的是<b>带 {@code SoundInstance} 的那个重载</b>，理由是
 * 「它内部转调内层重载，所以是所有音效音量的单一收敛点」。<b>前半句对，结论错</b>：
 * 转调关系成立，但 26.2 里<b>新播放的音效根本不经过外层重载</b>。
 * 本地 26.2 jar 字节码逐条核对（{@code SoundEngine.class}）：
 * <pre>
 * play(SoundInstance):
 *   @154  SoundInstance.getVolume()
 *   @177  SoundInstance.getSource()
 *   @189  SoundEngine.calculateVolume(F, SoundSource)      ← 直接调【内层】
 *
 * calculateVolume(SoundInstance)F 的调用方只有两个：
 *   tickInGameSound()V                          @117     ← 每 tick 更新「可 tick 的」音效
 *   lambda$refreshCategoryVolume$0(...)V        @19      ← 玩家改音量滑条时
 * </pre>
 * （两种方法互证：一遍按指令流走、一遍直接搜 Methodref 常量池下标的字节串，结果一致。）
 *
 * <p>于是旧注入的实际效果是：<b>耳鸣期间新响起来的声音一点没被压低</b>，
 * 只有「可 tick 的音效」在下一 tick 被压、以及改滑条时重算的那批被压 ——
 * 玩家看到的就是「有时闷有时不闷、毫无规律」，正是用户报的
 * 「耳鸣的音频实际上不生效，表现得没有逻辑」。1.21.11 上没这个毛病，
 * 是因为那条线上 {@code play} 走的是外层重载（该分支实测有效）。</p>
 *
 * <p>改成注入<b>内层</b>重载后，三条路径全部覆盖：
 * {@code play}（直接调它）、{@code tickInGameSound} 与
 * {@code refreshCategoryVolume}（都经外层重载转调它）。
 * 内层才是 26.2 真正的单一收敛点。</p>
 *
 * <h2>代价：内层拿不到 {@code SoundInstance}，耳鸣声改按 {@code SoundSource} 豁免</h2>
 * 耳鸣声本身必须豁免消声（否则会把自己也压没，变成「什么都听不见」而不是「耳朵在响」）。
 * 外层重载能靠 {@code instanceof StunRingingSound} 判断，内层只有
 * {@code (float, SoundSource)}。所幸 {@link DeafenState#getVolumeFactor} 本来就把
 * {@code MASTER / MUSIC / UI} 三个类别整个放行，而 {@code StunRingingSound} 正是用
 * {@code SoundSource.MASTER} 构造的 ⇒ 按类别即可豁免，不需要实例。
 *
 * <p><b>因此这条约束现在是硬性的</b>：耳鸣声<b>必须</b>继续用 {@code SoundSource.MASTER}。
 * 谁把它改成 {@code PLAYERS}（1.21.11 那条线用的就是 PLAYERS），耳鸣声就会被自己压没。
 * 相应地，原来那个靠反射猜名字的 {@code DeafenState#isRingingSound} 已删除 ——
 * 留着它会让人以为还有实例级豁免。</p>
 *
 * <h2>为什么用 {@code @ModifyReturnValue}</h2>
 * 语义就是「改返回值」，不需要 {@code CallbackInfoReturnable} 样板，
 * 且能与其他模组的同类注入自然叠加。本仓库已有多处先例
 * （{@code LootTableMixin} / {@code LivingEntityMixin}）。
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /**
     * 注入<b>内层</b>重载 —— 26.2 里所有音量计算的真正收敛点（证据见类注释）。
     *
     * <p>只挂这一处，<b>不要</b>同时再挂外层重载：外层会转调内层，
     * 两处都乘系数等于把音量压两次（0.01 × 0.01），耳鸣期间会近乎全静音。</p>
     */
    @ModifyReturnValue(
            method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
            at = @At("RETURN"))
    private float lrtactical$applyDeafen(float original, float volume, SoundSource source) {
        return original * DeafenState.getVolumeFactor(source);
    }
}
