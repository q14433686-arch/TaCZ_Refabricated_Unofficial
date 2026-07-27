package com.tacz.guns.mixin.client;

import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在 {@code renderAllFeatures} 的<b>阶段边界</b>插入瞄具掩码 pass。
 *
 * <h2>为什么必须是这个位置</h2>
 * 26.2 的绘制结构（字节码确认）：
 * <pre>
 * renderAllFeatures(storage) {
 *     PreparedFrame f = prepareFrame(storage);   // 只准备，不绘制
 *     f.executeSolid();                          // ← 各 executeXxx 内部才开关 pass
 *     f.executeTranslucent();
 *     f.executeTranslucentAfterTerrain();
 *     f.executeAlwaysOnTop();
 *     f.close();
 * }
 * </pre>
 * 也就是说<b>各阶段之间不在任何 render pass 内</b>，
 * 满足 {@code CommandEncoder#createRenderPass} 开头那句断言：
 * <pre>
 * if (this.isInRenderPass) throw new IllegalStateException(
 *     "Close the existing render pass before creating a new one!");
 * </pre>
 *
 * <p>这正是 r51 失败的反面。当时给 {@code ocular} 配了个 outputTarget 不同的
 * RenderType 走 collector，引擎按 RenderType 分批执行，于是
 * 「主 target → 掩码 target → 主 target」的切换被<b>零散穿插</b>进 solid 阶段内部，
 * 触发 {@code VK_ERROR_DEVICE_LOST}。vanilla 自己的多 target 从来都是
 * <b>成批地、在阶段边界</b>切 —— 本 mixin 就是回到那个模式。</p>
 *
 * <h2>地基已验证</h2>
 * 上一轮用一个空 pass 探针单独验过这个时机（实测预览块变绿），
 * 证明「阶段边界切 OutputTarget」不会重演 r51 的设备丢失。
 * 结论既已固化，探针便功成身退，本轮由 {@link ScopeMaskRenderer} 画真几何。
 *
 * <h2>注入点选择</h2>
 * 用 {@code INVOKE + executeSolid} 而不是 {@code HEAD}：
 * {@code HEAD} 处 {@code prepareFrame} 还没跑，
 * 而 {@code prepareFrame} 里有 {@code stagedVertexBuffer.upload()}；
 * 将来真正画掩码几何时必须在 upload <b>之后</b>才能拿到顶点数据。
 * 现在就把位置定对，避免后续再搬一次。
 *
 * <p>{@code shift = BEFORE} 保证掩码在 solid 之前完成 —— 镜身在 solid 阶段绘制，
 * 采样掩码时它必须已经就绪。</p>
 */
@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {

    @Inject(
            method = "renderAllFeatures",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeSolid()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void tacz$scopeMaskAtPhaseBoundary(SubmitNodeStorage storage, CallbackInfo ci) {
        // 【Step 2】画真正的目镜掩码。
        //
        // 上一轮的空 pass 探针已证明这个时机安全（实测预览块变绿），
        // 结论固化后探针即删除，不留死代码。
        ScopeMaskRenderer.renderAtPhaseBoundary();
    }
}
