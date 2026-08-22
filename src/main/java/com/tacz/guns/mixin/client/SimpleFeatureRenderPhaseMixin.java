package com.tacz.guns.mixin.client;

import com.tacz.guns.client.render.scope.ScopePipRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 镜内那一遍<b>不许</b>把本帧的提交节点吃掉 —— 主画面还要用同一批。
 *
 * <h2>症状</h2>
 * 一开镜，<b>镜外</b>的实体、方块实体、名牌全部消失，而<b>镜内</b>一切正常。
 * 光影开不开都一样。松开右键就恢复。
 *
 * <h2>成因（26.2 字节码实读）</h2>
 * 一帧里的实体等等是在 extract 阶段被「提交」到一个
 * {@code SubmitNodeStorage} 里的，而 {@code LevelRenderer#render} 开头会：
 * <pre>
 * featureRenderDispatcher.prepareFrame(storage)
 *   -> storage.drainPhases(phase -> phase.sortInto(preparedFrame))
 * </pre>
 * 而 {@code sortInto} 的<b>最后一句</b>是 {@code this.clear()}：
 * <pre>
 * 37: invokestatic  sortFeatureInto(...)   // 把节点拷进 PreparedFrame.allSubmits
 * 47: invokevirtual clear:()V              // ← 把自己清空
 * 50: return
 * </pre>
 * 名字里的 "drain" 是字面意思：<b>取一次就没了</b>。
 *
 * <p>而我们做的是「真·两遍渲染」的画中画：同一帧里先驱动一遍
 * {@code levelRenderer.render} 画镜内画面，再让 vanilla 画主画面。
 * 于是<b>第一遍（镜内）把节点全取走了，第二遍（主画面）拿到的是空的</b>。
 * 这正好解释了「镜内有、镜外没有」这个乍看反直觉的方向。
 *
 * <h2>为什么可以直接不清</h2>
 * {@code sortFeatureInto} 是把节点 {@code addAll} 进
 * {@code PreparedFrame.allSubmits}（{@code PhaseSubmitGrouper.acceptFeatureGroup}
 * 字节码实读），{@code PreparedGroup} 只记 {@code allSubmits} 上的下标区间。
 * 也就是说 <b>PreparedFrame 拿到的是一份拷贝</b>，与 phase 自己那些 list 再无瓜葛。
 * 所以镜内那一遍拷完之后把 phase 原样留着，主画面那一遍照样能再拷一次，
 * 两份互不影响。节点本身是只读地被绘制，重复绘制没有副作用 ——
 * 这跟 vanilla 一帧内多次绘制同一批几何是一回事。
 *
 * <h3>为什么取消整个 sortInto 尾巴，而不是 @Redirect 掉那个 clear()</h3>
 * 两个 phase 实现清空自己的写法不一样：本类是调自己的 {@code clear()}，
 * {@code TranslucentFeatureRenderPhase} 则是就地 {@code submits.clear()} +
 * {@code distances.clear()} 两句。但<b>两边都把清空放在方法最后</b>，
 * 所以「在第一句清空之前 cancel 掉」这一招对两边都成立，且不依赖各自的写法。
 * 尤其对 translucent 那边很关键：它那两个 list 是平行的，
 * 只清一个会让 phase 处于错位状态，而 cancel 是两句一起跳过。
 *
 * <h2>作用范围</h2>
 * 只在镜内那一次 {@code levelRenderer.render} 期间生效。其余任何时候
 * （包括紧接着的主画面那一遍）{@code sortInto} 照常清空自己，
 * 否则节点会一直堆到下一帧。
 *
 * @see TranslucentFeatureRenderPhaseMixin
 */
@Mixin(SimpleFeatureRenderPhase.class)
public abstract class SimpleFeatureRenderPhaseMixin {

    @Inject(
            method = "sortInto",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/phase/SimpleFeatureRenderPhase;clear()V",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void tacz$keepSubmitsForTheMainPass(FeatureRenderPhase.Output output, CallbackInfo ci) {
        if (ScopePipRenderer.isInsideScopeLevelRender()) {
            // 节点已经拷进镜内那一遍的 PreparedFrame 了，这里只是跳过「清空自己」。
            // clear() 是本方法的最后一句，所以 cancel 与「跳过它」完全等价。
            ci.cancel();
        }
    }
}
