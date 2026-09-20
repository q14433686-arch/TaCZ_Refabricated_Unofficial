package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 取 {@code GameRenderer#hudProjection} —— 第一人称视模（手部 pass）实际使用的那个投影。
 *
 * <h2>为什么需要它</h2>
 * <p>{@code ScopeMaskRenderer#writeHullFill} 要在 CPU 侧做凸包，必须拿到与着色器
 * 完全同源的投影矩阵。26.2 时的做法是把 {@code RenderSystem.getProjectionMatrixBuffer()}
 * 这个 UBO 读回来（{@code slice.map(true, false)}）。<b>26.3 起这条路断了</b>：
 * renderpearl 的 {@code GlBuffer$Direct.map} 对没有 READ 用途标记的 buffer 直接抛
 * {@code IllegalStateException: Buffer is not readable}（2026-09-18 01:49 日志）。
 * 结果每帧回退到逐立方体描摹 —— 掩码形状退化，高倍镜的裁剪边缘因此不准。</p>
 *
 * <h2>为什么取 hudProjection 而不是 cameraState.projectionMatrix</h2>
 * <p>两者<b>不是同一个矩阵</b>。世界用 {@code cameraState.projectionMatrix}（正常 FOV），
 * 而手部 pass 在绘制前会把投影换成 {@code hudProjection}
 * （{@code GameRenderer:679-683}，用的是 {@code cameraState.hudFov} 与 0.05F 近平面）。
 * 瞄具是第一人称视模，它的顶点走的是后者；拿世界那个去算凸包，掩码会整体错位缩放。</p>
 *
 * <p>{@code Projection#getMatrix(Matrix4f)} 返回的正是 {@code hud3dProjectionMatrixBuffer}
 * 上传给 GPU 的同一份数据（{@code GameRenderer:683} 把这个 Projection 交给
 * {@code ProjectionMatrixBuffer#getBuffer} 编码进 UBO），因此「CPU 凸包」与
 * 「GPU 着色」严丝合缝 —— 这正是原注释所要求的同源性，只是换了个不需要回读的来源。</p>
 */
@Mixin(GameRenderer.class)
public interface GameRendererProjectionAccessor {
    @Accessor("hudProjection")
    Projection tacz$getHudProjection();

    /**
     * 取 {@code GameRenderer#fogRenderer}：掩码 pass 需要它的
     * {@code getBuffer(FogMode.NONE)}（全零颜色、起止 = MAX_VALUE 的「空雾」UBO），
     * 把 {@code core/position.fsh} 里的 {@code apply_fog} 彻底关掉。
     * 见 {@code ScopeMaskRenderer#drawMask}。
     */
    @Accessor("fogRenderer")
    FogRenderer tacz$getFogRenderer();
}
