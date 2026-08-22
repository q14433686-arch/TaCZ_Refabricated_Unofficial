package com.tacz.guns.mixin.client;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    /**
     * 全程<b>只有这一个</b> PreparedFrame 实例，主画面那一遍与镜内那一遍轮流用它。
     * 正因为是同一个，镜内那遍漏关就会把主画面那遍顶掉。
     */
    @Shadow
    @Final
    private FeatureRenderDispatcher.PreparedFrame preparedFrame;

    @Unique
    private static boolean tacz$loggedFrameRecovery;

    /**
     * 把镜内那一遍失败时漏关的 PreparedFrame 关掉。
     *
     * <h2>不做会怎样</h2>
     * {@code LevelRenderer#render} 是「{@code prepareFrame} → 执行 frame graph →
     * {@code close}」的直写结构，中间抛异常 {@code close()} 就没了。
     * 而我们是在同一帧里<b>先</b>驱动一遍 {@code levelRenderer.render} 画镜内画面、
     * <b>再</b>让 vanilla 画主画面的，于是镜内那遍留下的「在用」标志会把主画面那遍
     * 直接顶成 {@code IllegalStateException: PreparedFrame already in use}。
     *
     * <p>结果就是：{@code ScopePipRenderer} 那边明明捕获了异常、打印了
     * 「PIP disabled, falling back to whole-screen FOV zoom」，游戏却仍旧崩了，
     * 而且崩溃报告里<b>只剩这个二次错误</b>，真正的病因（通常在 Voxy 或 Iris 那侧）
     * 一个字都看不见。修这一处，等于让所有镜内渲染的失败都退回成「这一帧没有 PIP」，
     * 同时把真实原因完整留在日志里。
     *
     * <h2>为什么调 close() 而不是把 context 抹成 null</h2>
     * {@code close()} 做的是<b>真正的收尾</b>：给每个 FeatureRenderer 调
     * {@code finishExecute(context)}、给 {@code stagedVertexBuffer} 调 {@code endDraw()}
     * （与 {@code prepareFrameWithContext} 里的 {@code upload()} 配对）、
     * 再清掉本帧攒下的 submit 列表。直接抹字段会把这些全跳过 ——
     * 顶点缓冲一直停在 draw 状态，下一帧照样出问题。
     *
     * <h2>为什么只在「刚失败过」时才动它</h2>
     * 这个标志由 {@code ScopePipRenderer} 在它自己的 catch 里置位，取一次即清。
     * 正常帧上这里读一个 volatile boolean 就返回，既不改变任何行为，
     * 也绝不会去碰一个本来就该开着的 frame。
     */
    @Inject(method = "prepareFrame", at = @At("HEAD"))
    private void tacz$releaseFrameLeakedByFailedScopePass(
            SubmitNodeStorage storage,
            CallbackInfoReturnable<FeatureRenderDispatcher.PreparedFrame> cir) {
        if (!ScopePipRenderer.consumePreparedFrameLeak()) {
            return;
        }
        // 失败发生在 prepareFrame 之前（比如投影都没建起来）时这里是 null，
        // 什么都没漏，直接放行。
        if (((PreparedFrameAccessor) this.preparedFrame).tacz$context() == null) {
            return;
        }
        this.preparedFrame.close();
        if (!tacz$loggedFrameRecovery) {
            tacz$loggedFrameRecovery = true;
            GunMod.LOGGER.warn("[TACZ Scope] The scope pass left this frame's PreparedFrame open when "
                    + "it failed; closed it so the main view can still render. The real cause is the "
                    + "exception logged just above this line - without this recovery the game would "
                    + "have crashed here with a misleading 'PreparedFrame already in use'.");
        }
    }

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
        // 【镜内画中画】紧跟掩码之后合成。三者的先后关系是硬约束：
        //
        //   掩码           -> 知道镜内是哪些像素
        //   合成（这一句）  -> 那些像素被贴上离屏渲染的放大世界
        //   executeSolid…  -> 镜身在镜内 discard（PIP 画面得以留住）；
        //                     准星反向裁剪只画镜内（浮在 PIP 画面之上）
        //
        // 往前挪掩码还没就绪，往后挪（比如手持渲染之后）准星会被 PIP 盖掉。
        ScopePipRenderer.compositeAtPhaseBoundary();
    }
}
