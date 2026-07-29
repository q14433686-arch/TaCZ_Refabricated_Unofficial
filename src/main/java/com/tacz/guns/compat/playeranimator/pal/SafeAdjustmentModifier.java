package com.tacz.guns.compat.playeranimator.pal;

import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;

/**
 * 【第 37 轮】绕开 PAL 1.2.5 的一处空指针：{@code AdjustmentModifier#get3DTransform}
 * 会在自身 {@code data} 尚未初始化时解引用它。
 *
 * <h2>崩溃现场</h2>
 * <pre>
 * NPE: Cannot invoke "AnimationData.getPartialTick()" because "this.data" is null
 *   at AdjustmentModifier.get3DTransform(AdjustmentModifier.java:173)
 *   at AnimationController.get3DTransform(AnimationController.java:866)
 *   at AnimationStack.get3DTransform(AnimationStack.java:46)
 *   at AvatarAnimManager.updatePart(AvatarAnimManager.java:91)
 *   at PlayerModel.handler$bek000$player_animation_library$setupPlayerAnimation
 * </pre>
 *
 * <h2>为什么会 null（对 PAL 源码逐行确认）</h2>
 * {@code AdjustmentModifier} 里 {@code private AnimationData data} <b>只有两处赋值</b>：
 * <pre>
 *   public void tick(AnimationData state)      { ...; this.data = state; }
 *   public void setupAnim(AnimationData state) { ...; this.data = state; }
 * </pre>
 * 而 {@code get3DTransform} 第 171/173 行<b>无条件</b>使用它：
 * <pre>
 *   Optional&lt;PartModifier&gt; partModifier = source.apply(bone.getName(), data);
 *   float fade = getFadeIn() * getFadeOut(data.getPartialTick());   // ← NPE
 * </pre>
 *
 * <p>关键在于 {@code AnimationStack}（第 40-48 行）对这两条路径用的是<b>同一个</b>门禁
 * {@code layer.right().isActive()}，但两者并不保证成对发生：</p>
 * <pre>
 *   get3DTransform(bone) : if (layer.isActive()) layer.get3DTransform(bone);
 *   setupAnim(state)     : if (layer.isActive()) layer.setupAnim(state);
 * </pre>
 * {@code isActive()} 读的是 {@code animationState.isActive()} —— 一个会随动画
 * 播放/淡出实时变化的量。只要某一帧里 controller 先变为 active、
 * 而 {@code setupAnim}/{@code tick} 尚未在该 controller 上跑过一次，
 * {@code get3DTransform} 就会拿着 null 的 {@code data} 进去。
 *
 * <p>这解释了用户报告的触发条件「第三人称持枪退出存档、再进入即崩」：
 * 重进世界时 PAL 的 controller 由 {@code ANIMATION_DATA_FACTORY} 重新创建，
 * {@code data} 自然是 null；而 TACZ 在渲染阶段就会调 {@code play(...)} 把
 * ROTATION 层置为 active。渲染早于该层的第一次 tick，于是当帧直接 NPE。
 * 空手时 TACZ 不会激活该层，第一人称不渲染 PlayerModel 本体 ——
 * 所以必须「第三人称 + 持枪」两个条件同时满足，与实测完全一致。</p>
 *
 * <h2>本类的处理</h2>
 * {@code enabled} 是 {@code AdjustmentModifier} 的 <b>public</b> 字段，
 * 且 {@code get3DTransform} 的<b>第一行</b>就是：
 * <pre>
 *   if (!enabled) { super.get3DTransform(bone); return; }   // 不碰 data
 * </pre>
 * 也就是说父类自己留了一条完全不接触 {@code data} 的短路。
 * 因此这里在 {@code data} 就绪之前把 {@code enabled} 置 false，
 * 让父类走那条安全分支；一旦 {@code tick}/{@code setupAnim} 跑过（{@code data} 已写入）
 * 就恢复为 true，行为与原版完全一致。
 *
 * <p>这样做的好处是<b>不改 PAL、不加 mixin</b>，只用它公开的字段与继承点，
 * PAL 将来修好这个 NPE 也不会与本类冲突（届时 {@code seen} 恒为 true，本类退化为透明包装）。</p>
 */
final class SafeAdjustmentModifier extends AdjustmentModifier {

    /** {@code tick}/{@code setupAnim} 是否已至少跑过一次 —— 即父类的 {@code data} 是否非 null。 */
    private boolean dataReady = false;

    SafeAdjustmentModifier(Function<String, Optional<PartModifier>> source) {
        super(source);
    }

    @Override
    public void tick(AnimationData state) {
        super.tick(state);
        this.dataReady = true;
    }

    @Override
    public void setupAnim(AnimationData state) {
        super.setupAnim(state);
        this.dataReady = true;
    }

    @Override
    public void get3DTransform(@NotNull PlayerAnimBone bone) {
        if (!dataReady) {
            // 父类此时 data == null，直接调用必 NPE。
            // 借它自己的 enabled 短路走「不做调整、只透传」的分支。
            boolean previous = this.enabled;
            this.enabled = false;
            try {
                super.get3DTransform(bone);
            } finally {
                this.enabled = previous;
            }
            return;
        }
        super.get3DTransform(bone);
    }
}
