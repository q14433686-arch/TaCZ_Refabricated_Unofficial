package com.tacz.guns.mixin.client;

import com.tacz.guns.client.render.scope.ScopePipRenderer;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link SimpleFeatureRenderPhaseMixin} 的另一半 —— 半透明那条队列。
 *
 * <p>病因与理由完全相同（见那边的完整说明），只有「清空自己」的写法不一样：
 * 这个类没有 {@code clear()} 方法，而是在 {@code sortInto} 末尾就地清两个
 * <b>平行</b>的 list（26.2 字节码实读）：
 * <pre>
 * 52: invokeinterface FeatureRenderPhase$Output.accept(...)   // 按 sortIndices 顺序送出
 * 67: invokeinterface java/util/List.clear:()V                // ← this.submits
 * 76: invokeinterface FloatList.clear:()V                     // ← this.distances
 * </pre>
 *
 * <p>注入点选在<b>第一句</b> {@code List.clear()} 之前并 cancel，两句一起跳过。
 * 这一点在这里是硬性要求：{@code submits} 与 {@code distances} 一一对应，
 * 只清掉其中一个会让这个 phase 处于错位状态，下一次排序就会拿错距离。
 *
 * <p>整个 {@code sortInto} 里只有这一处 {@code java/util/List.clear()}
 * （其余对 {@code submits} 的调用是 {@code isEmpty()} 与 {@code get(int)}），
 * 所以这个注入点没有歧义。
 */
@Mixin(TranslucentFeatureRenderPhase.class)
public abstract class TranslucentFeatureRenderPhaseMixin {

    @Inject(
            method = "sortInto",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;clear()V",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true
    )
    private void tacz$keepSubmitsForTheMainPass(FeatureRenderPhase.Output output, CallbackInfo ci) {
        if (ScopePipRenderer.isInsideScopeLevelRender()) {
            ci.cancel();
        }
    }
}
