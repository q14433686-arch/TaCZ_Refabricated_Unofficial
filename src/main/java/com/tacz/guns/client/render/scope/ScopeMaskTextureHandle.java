package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.tacz.guns.GunMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * 把离屏掩码 target 的纹理伪装成一张<b>普通注册纹理</b>，好让 RenderSetup 能绑定它。
 *
 * <h2>为什么需要这层包装</h2>
 * {@code RenderSetup.RenderSetupBuilder#withTexture(String, Identifier)} 只接受
 * {@code Identifier}，而 {@code RenderSetup#prepareTextures} 内部走的是
 * <pre>textureManager.getTexture(binding.location())</pre>
 * （字节码偏移 156-159 确认）—— 也就是说它<b>只能绑定注册在 TextureManager 里的纹理</b>，
 * 没有任何重载能直接塞一个现成的 {@code GpuTextureView}。
 *
 * <p>但 {@code AbstractTexture} 的三个字段 {@code texture/textureView/sampler}
 * 都是 {@code protected}（字节码确认 flags=0x4），子类可以直接写。
 * 于是这里做一个「空壳纹理」：不自己创建任何 GPU 资源，
 * 每帧把掩码 target 的 view 塞进这三个字段，再注册到 TextureManager。
 * 引擎照常按 Identifier 查表，拿到的就是我们的掩码。
 *
 * <h2>生命周期：为什么 close() 是空的</h2>
 * 本类<b>不拥有</b>那张纹理 —— 它属于 {@link ScopeMaskTarget}，由那边创建与销毁。
 * 若在这里 close，会把别人的资源提前释放掉（TextureManager 在重载时会
 * 对注册项调用 close）。所以覆写为 no-op，把所有权边界划清楚。
 *
 * <p>同理，{@code sampler} 每帧刷新：窗口尺寸变化会让 {@link ScopeMaskTarget}
 * 重建 target，旧的 view 就失效了，必须重新指向新的。
 */
@Environment(EnvType.CLIENT)
public final class ScopeMaskTextureHandle extends AbstractTexture {

    /** 掩码纹理在 TextureManager 里的注册名。 */
    public static final Identifier ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "scope_mask");

    private static ScopeMaskTextureHandle instance;
    private static boolean registered = false;

    private ScopeMaskTextureHandle() {
    }

    /**
     * 让 {@link #ID} 指向当前掩码 target 的纹理。每帧调用（幂等）。
     *
     * <p>必须在掩码绘制<b>之后</b>、镜身绘制<b>之前</b>调用，
     * 这样镜身采样到的才是当帧的掩码。
     *
     * @return 绑定成功返回 true；掩码不可用时返回 false（调用方据此回退到普通渲染）
     */
    public static boolean syncToMaskTarget() {
        var target = ScopeMaskTarget.current();
        if (target == null || !ScopeMaskTarget.isAvailable()) {
            return false;
        }
        try {
            if (instance == null) {
                instance = new ScopeMaskTextureHandle();
            }
            // 每帧重新指向：窗口尺寸变化时 ScopeMaskTarget 会重建 target，
            // 旧 view 随之失效，缓存住会拿到悬空引用。
            instance.texture = target.getColorTexture();
            instance.textureView = target.getColorTextureView();
            // NEAREST：掩码是二值数据，线性过滤会在边缘产生 0.5 附近的中间值，
            // 让 shader 里的 `> 0.5` 判定在边界抖动，视觉上是一圈毛边。
            instance.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

            if (!registered) {
                Minecraft.getInstance().getTextureManager().register(ID, instance);
                registered = true;
            }
            return true;
        } catch (Exception e) {
            GunMod.LOGGER.error("[TACZ Scope] Failed to expose mask texture; scope body clipping disabled.", e);
            return false;
        }
    }

    /**
     * 不释放任何东西 —— 纹理归 {@link ScopeMaskTarget} 所有。
     *
     * <p>TextureManager 在资源重载时会对注册项调用 {@code close()}，
     * 若这里真去释放，就会把 target 还在用的纹理提前销毁。
     */
    @Override
    public void close() {
        // 故意留空，见 javadoc。
    }
}
