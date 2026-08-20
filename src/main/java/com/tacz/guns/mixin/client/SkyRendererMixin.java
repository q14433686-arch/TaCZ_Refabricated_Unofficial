package com.tacz.guns.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tacz.guns.client.render.scope.ScopePipRenderer;
import net.minecraft.client.renderer.SkyRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让天空渲染跟随「镜内二次渲染」的输出重定向。
 *
 * <h2>它补的是哪个洞</h2>
 * 二次渲染靠注入 {@code GameRenderer#mainRenderTarget()} 把整遍世界引到离屏 target。
 * 全 26.2 客户端里调用该方法的类<b>只有 {@link SkyRenderer} 一个会把结果缓存下来</b>：
 * <pre>
 * // LevelRenderer 偏移 64-87
 * this.skyRenderer = new SkyRenderer(textureManager, atlasManager, gameRenderer.mainRenderTarget());
 * // SkyRenderer
 * private final RenderTarget renderTarget;   // 构造时定死，之后再不重新问
 * </pre>
 * 其余调用方（{@code ChunkSectionLayerGroup}、{@code CloudRenderer}、
 * {@code QuadParticleFeatureRenderer}、{@code WorldBorderRenderer}、{@code OutputTarget}，
 * 以及 Sodium 的 {@code TerrainRenderPass}）都是<b>每次用的时候现问</b>，所以重定向对它们天然生效。
 *
 * <p>后果是：{@code skyRenderer} 是在进世界时用<b>真实主 target</b> 构造的，
 * 于是镜内那一遍的天空、太阳、月亮、星星<b>全部画到了屏幕上</b> ——
 * 表现为放大的天空糊在镜筒外面，和正常画面撞在一起。
 * 用户实测原话：「rerender 本该只在镜内的放大画面溢出到镜外，两个渲染撞在一起」，
 * 且<b>开不开光影都一样</b> —— 正是因为这条与光影无关，纯粹是缓存引用的问题。
 *
 * <h2>为什么改字段读取而不是别的</h2>
 * 那个字段是 {@code private final}，且在 {@code renderSkyDisc} / {@code renderDarkDisc} /
 * {@code renderSun} / {@code renderMoon} / {@code renderStars} 里各读两次
 * （颜色附件 + 深度附件）。与其逐个方法注入，不如直接改<b>字段读取表达式</b>的值：
 * 一处覆盖全部读点，将来 vanilla 增删调用点也不用跟着改。
 *
 * <p>{@code opcode = GETFIELD} 把构造函数里那次 {@code putfield} 排除在外，
 * 所以字段本身仍然是构造时那个真实主 target，我们只在<b>读的瞬间</b>换掉。
 * 重定向窗口之外 {@link ScopePipRenderer#redirectTarget()} 恒返回 null，
 * 此时这里原样返回 {@code original}，等于没有这个 mixin。
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @ModifyExpressionValue(
            method = "*",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/SkyRenderer;renderTarget:Lcom/mojang/blaze3d/pipeline/RenderTarget;",
                    opcode = Opcodes.GETFIELD
            )
    )
    private RenderTarget tacz$redirectSkyTargetForScopePip(RenderTarget original) {
        RenderTarget scopeTarget = ScopePipRenderer.redirectTarget();
        return scopeTarget != null ? scopeTarget : original;
    }
}
