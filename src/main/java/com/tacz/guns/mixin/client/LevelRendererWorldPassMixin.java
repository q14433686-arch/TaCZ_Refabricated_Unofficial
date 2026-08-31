package com.tacz.guns.mixin.client;

import cn.sh1rocu.tacz.compat.meshloader.render.PolyMeshGpuRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 「此刻在不在 {@code LevelRenderer.render} 里」的括号标志。
 *
 * <p>{@code PreparedFrameSolidMixin} 挂在 {@code executeSolid} 上，但那个方法
 * 有四类调用者（世界帧图 / GUI 的 renderAllFeatures / 手部 / renderLevel 偏移
 * 560 的收尾调用）。世界 GPU 表只许在<b>世界帧图那一类</b>消费 —— 判据就是
 * 本标志：只有 {@code LevelRenderer.render} 的帧图执行（字节码偏移 572，
 * 在 MV 栈 push viewRotation 的 30-45 与 pop 的 591 之间）落在这个括号内，
 * 其余三类都在括号外。</p>
 *
 * <p>镜内那一遍是我们自己调的 {@code mc.levelRenderer.render}，同样会进这个
 * 括号 —— 正确：镜内世界枪就该在那一遍画（两遍内容一致裁定），
 * {@code renderWorldAfterSolid} 内部再按 {@code isInsideScopeLevelRender}
 * 区分「画而不清表」。</p>
 *
 * <p>RETURN 注入在异常路径不触发（镜内那遍的失败被 ScopePipRenderer 捕获），
 * 标志可能泄漏到帧尾 —— {@code PolyMeshGpuRenderer.beginFrame} 每帧兜底归零，
 * 且下一次 render 的 HEAD 会重新置位，自愈。</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererWorldPassMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void tacz$beginLevelRender(CallbackInfo ci) {
        PolyMeshGpuRenderer.setInsideLevelRender(true);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void tacz$endLevelRender(CallbackInfo ci) {
        PolyMeshGpuRenderer.setInsideLevelRender(false);
    }
}
