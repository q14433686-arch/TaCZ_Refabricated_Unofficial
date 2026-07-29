package me.xjqsh.lrtactical.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「有害」类状态效果的通用外壳。
 *
 * <p><b>本身没有任何逻辑</b>，只是一个可被查询的状态标记 ——
 * 与上游一致（上游 {@code HarmfulEffect} 全类仅 10 行）。
 *
 * <p>真正的效果由<b>客户端表现层</b>按「玩家是否携带该效果」自行实现：
 * <ul>
 *   <li>{@code BLIND} → {@code BlindnessOverlay} 往屏幕糊一层白/黑；</li>
 *   <li>{@code DEAFENED} → {@code DeafenSoundHandler} 压低所有音效音量。</li>
 * </ul>
 * 因此<b>只移植效果注册而不做表现层，扔出闪光弹会毫无反应</b>
 * （见 COMPAT_AND_ROADMAP 6.4 的分析）。本轮两者一并实现。
 *
 * <p>26.2 说明：{@code MobEffect} 的构造器是 {@code protected}
 * （字节码确认），但类非 final，子类正常调用即可。
 */
public class HarmfulEffect extends MobEffect {
    public HarmfulEffect(int color) {
        super(MobEffectCategory.HARMFUL, color);
    }
}
