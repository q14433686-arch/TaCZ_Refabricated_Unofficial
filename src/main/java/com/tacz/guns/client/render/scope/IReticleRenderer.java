package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * 准星（分划）绘制策略。
 *
 * <p>把「准星怎么画」从 {@code BedrockAttachmentModel} 里抽出来，
 * 让全息红点 / 老式蚀刻 / 混合镜 / 自定义各走各的实现，
 * 并允许第三方枪包通过 {@link #priority()} 覆盖内置策略。</p>
 *
 * <h2>为什么需要这层抽象</h2>
 * 三类准星的物理行为完全不同：
 * <ul>
 *   <li><b>全息/红点</b>：准直光学系统，准星<b>不随枪体贴合</b>，
 *       而是随视线方向漂移（无视差），且恒定发光；</li>
 *   <li><b>蚀刻分划</b>：物理刻在镜片玻璃上，<b>完全跟随枪体</b>，不发光；</li>
 *   <li><b>混合</b>：几何跟随枪体，但其中一段发光。</li>
 * </ul>
 * 用 if/else 硬写会迅速失控，因此定义成策略接口。
 *
 * <h2>实现约定</h2>
 * <ol>
 *   <li>只能走 <b>submit 快照</b>链路（{@code BedrockRenderSnapshot}），
 *       <b>不要</b>调用 legacy {@code renderTempPart}——它在 26.1.2 是 no-op；</li>
 *   <li>若修改了 {@link BedrockPart#visible}，必须在 {@code finally} 里还原：
 *       模型节点是<b>跨帧共享</b>的，不还原会污染第三人称与物品栏；</li>
 *   <li>实现应当是无状态的（单例即可），所有每帧数据从 {@link Context} 取。</li>
 * </ol>
 */
public interface IReticleRenderer {

    /**
     * 本策略是否适用于该瞄具。
     *
     * @param nodes 已解析的准星节点集合
     */
    boolean matches(ScopeNodeSet nodes);

    /**
     * 提交准星几何。调用时机在 {@code super.submit(...)} <b>之后</b>，
     * 以保证准星盖在镜身之上。
     */
    void submitReticle(Context ctx, ScopeNodeSet nodes);

    /**
     * 优先级，数值大者优先。内置实现一律为 0，
     * 第三方枪包/附属模组注册 {@code > 0} 的实现即可覆盖。
     */
    default int priority() {
        return 0;
    }

    /**
     * 一帧内准星绘制所需的全部上下文。
     *
     * @param poseStack      当前矩阵（已包含瞄具自身的变换）
     * @param collector      26.1.2 的提交收集器
     * @param displayContext 显示上下文（第一人称 / 第三人称 / GUI…）
     * @param baseRenderType 瞄具本体使用的 RenderType（贴图已绑定）
     * @param light          继承的光照
     * @param overlay        overlay 坐标
     * @param aimingProgress 开镜进度 0~1，可用于淡入淡出
     * @param baseRenderType 准星应当使用的 RenderType。掩码生效时它是「反向裁剪」版
     *                       （只在目镜投影内绘制）；否则是普通的 entityCutout。
     * @param maskActive     本帧目镜掩码是否真的生效。
     *                       <p>{@link EtchedReticleRenderer} 必须看这个标志：
     *                       {@code division} 里混着大块遮光板，只有在掩码把它们裁掉时
     *                       才能安全绘制，否则会糊住屏幕（第 9 轮的教训）。</p>
     * @param deferToIrisTranslucent true 时只冻结快照，交由
     *                                {@link ScopeLateReticleState} 在 Iris 的较晚
     *                                {@code HAND_TRANSLUCENT} pass 提交。
     * @param deferToIrisFinalOverlay true 时保留同一份 3D 快照，交由
     *                                {@link ScopeFinalOverlayState} 在 Iris final composite
     *                                之后提交，以避开不可覆盖的屏幕空间雾。
     */
    record Context(PoseStack poseStack,
                   OrderedSubmitNodeCollector collector,
                   ItemDisplayContext displayContext,
                   RenderType baseRenderType,
                   RenderType illuminatedRenderType,
                   int light,
                   int overlay,
                   float aimingProgress,
                   boolean maskActive,
                   boolean deferToIrisTranslucent,
                   boolean deferToIrisFinalOverlay) {
    }
}
