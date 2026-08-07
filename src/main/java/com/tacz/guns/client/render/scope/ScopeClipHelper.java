package com.tacz.guns.client.render.scope;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 判断第一人称枪体/手臂是否应当按<b>目镜掩码</b>裁掉（开镜后目镜圆内 discard）。
 *
 * <p>这是 {@code GunItemRendererWrapper}（枪体）与 {@code LeftHandRender}/{@code RightHandRender}
 * （手臂）三处共用的一份判定，条件与 {@code BedrockAttachmentModel#submit} 里
 * 是否启用镜身/准星掩码裁剪<b>完全一致</b>，避免各写一份出现漂移。</p>
 *
 * <p>判据逐条对应镜身那条路径（{@code resolveBodyRenderType}）：</p>
 * <ul>
 *   <li>掩码总开关 {@code SCOPE_MASK_ENABLE} 必须开；</li>
 *   <li>光影包接管时（{@code IrisCompat.shouldDisableScopeMaskUnderShaderPack()}）
 *       镜身/准星本就走安全回退，枪体/手臂也必须同步回退，否则会与镜身错位；</li>
 *   <li>开镜进度必须超过 {@link #AIM_CLIP_START} —— 与 {@code AIM_CLIP_START} 同一门槛，
 *       未开镜或刚开始开镜时不裁，避免腰射状态出问题；</li>
 *   <li>{@code ScopeMaskTextureHandle.syncToMaskTarget()} 必须可用（掩码 target 建得出来）。</li>
 * </ul>
 *
 * <p>注意：本方法<b>不</b>要求「真的装了一把有目镜的瞄具」。因为掩码内容是每帧现画的：
 * 没装瞄具 / 用机瞄时，掩码 target 被清成全黑 → {@code insideOcular} 全为 false →
 * 裁剪版 shader 一个像素都不会 discard → 枪体/手臂照常全量渲染。这是一个<b>安全退化</b>：
 * 调用方永远可以无脑用裁剪版 RenderType / 隐藏手臂，最坏也就是没装瞄具时什么都不裁，
 * 绝不会把腰射状态的枪裁没。</p>
 */
@Environment(EnvType.CLIENT)
public final class ScopeClipHelper {

    /**
     * 开始按目镜掩码裁剪的开镜进度门槛。与 {@code BedrockAttachmentModel.AIM_CLIP_START}
     * 取同一值（0.02），保证枪体/手臂与镜身、准星在同一条时间线上生效。
     */
    public static final float AIM_CLIP_START = 0.02f;

    private ScopeClipHelper() {
    }

    /** @return 当前第一人称是否处于「目镜掩码生效」的开镜状态 */
    public static boolean isScopedMaskActive() {
        if (!RenderConfig.SCOPE_MASK_ENABLE.get()) {
            return false;
        }
        if (IrisCompat.shouldDisableScopeMaskUnderShaderPack()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        IClientPlayerGunOperator op = IClientPlayerGunOperator.fromLocalPlayer(player);
        float aimingProgress = op.getClientAimingProgress(
                Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (aimingProgress <= AIM_CLIP_START) {
            return false;
        }
        // 幂等；掩码 target 建不出来时返回 false（回退到原渲染）。
        return ScopeMaskTextureHandle.syncToMaskTarget();
    }
}
