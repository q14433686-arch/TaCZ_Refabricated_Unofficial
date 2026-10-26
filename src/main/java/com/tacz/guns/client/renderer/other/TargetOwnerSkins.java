package com.tacz.guns.client.renderer.other;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * 标靶 / 标靶矿车头像的皮肤贴图查找（客户端）。
 *
 * <p>走原版 {@code PlayerSkinRenderCache}（{@code Minecraft#playerSkinRenderCache()}）：它拿
 * {@link ResolvableProfile} 去客户端的 {@code ProfileResolver} <b>异步</b>按名字解析出完整档案
 * （含 {@code textures} 属性）再下载皮肤；还没解析好时 {@code getOrDefault} 先给默认皮肤，
 * 就绪后下一帧自动换成真皮肤 —— 与原版玩家头颅同一条路径。离线服 / 名字不是真实玩家 / 网络失败时
 * 一直是默认皮肤。结果按档案缓存（{@code PlayerSkinRenderCache#CACHE_DURATION}），每帧调用很便宜。</p>
 *
 * <p>不用 {@code SkinManager#createLookup(GameProfile, boolean)}：它自己不查人，只处理档案已带的
 * {@code textures} 属性；只有名字的档案永远拿不到真皮肤（见 {@code TargetOwnerProfiles} 类注释）。</p>
 */
@Environment(EnvType.CLIENT)
public final class TargetOwnerSkins {
    private TargetOwnerSkins() {
    }

    /**
     * @return 头像应使用的皮肤贴图；任何异常都退回该档案对应的默认皮肤，
     * 渲染线程不会因为一个头像而崩。
     */
    public static Identifier texture(ResolvableProfile profile) {
        try {
            return Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile).playerSkin().body().texturePath();
        } catch (RuntimeException e) {
            // 例如异步解析的 future 以异常结束时 getNow 会抛 CompletionException。
            return DefaultPlayerSkin.get(profile.partialProfile()).body().texturePath();
        }
    }
}
