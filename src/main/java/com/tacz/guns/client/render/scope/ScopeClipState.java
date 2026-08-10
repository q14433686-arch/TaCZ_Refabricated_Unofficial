package com.tacz.guns.client.render.scope;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * 「本帧第一人称正在用<b>筒镜</b>开镜、掩码已激活」的跨模型开关。
 *
 * <h2>它解决什么问题</h2>
 * 镜内窗口裁切只作用于<b>瞄具配件自己</b>的几何（镜身/目镜/准星）。枪体
 * （{@code BedrockGunModel}）与其它配件（前瞄、制退器、握把等）各自走独立的
 * {@code submit}，不知道「本帧有一面目镜掩码正在生效」。没有这个开关，
 * 它们就永远用普通 RenderType —— 开镜时枪体/配件照进镜内窗口
 * （用户反馈：「未能镜内裁切掉枪体、配件」）。
 *
 * <h2>生命周期</h2>
 * <ul>
 *   <li>置位：<b>筒镜</b>（{@code isScope}，红点/全息不算）配件在
 *       {@code BedrockAttachmentModel#submit} 里判定 {@code maskable} 成功后置位；
 *       枪体在 {@code GunItemRendererWrapper#renderFirstPerson} 里用同一条件自行判定
 *       （枪体的 RenderType 在瞄具提交之前就要选定，读不了这个开关）。</li>
 *   <li>清除：{@code ScopeMaskRenderer#renderAtPhaseBoundary} 的掩码 pass 之后。
 *       开关只影响<b>提交阶段</b>的 RenderType 选择，绘制阶段已无关紧要，
 *       但清掉可以避免枪械收起后残留到下一帧。</li>
 * </ul>
 *
 * <p>只被第一人称路径消费（各 resolve 方法都有 {@code transformType.firstPerson()}
 * 门禁），第三人称/物品栏不受影响。</p>
 */
@Environment(EnvType.CLIENT)
public final class ScopeClipState {

    private static boolean scopeAimActive = false;

    private ScopeClipState() {
    }

    public static void setScopeAimActive(boolean active) {
        scopeAimActive = active;
    }

    public static boolean isScopeAimActive() {
        return scopeAimActive;
    }
}
