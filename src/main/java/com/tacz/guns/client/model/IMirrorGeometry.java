package com.tacz.guns.client.model;

import com.tacz.guns.client.model.bedrock.BedrockPart;

import javax.annotation.Nullable;

/**
 * 标记型接口：告诉快照遍历器"在<b>本节点的变换下</b>，把另一个节点的几何再画一遍"。
 *
 * <p><b>为什么需要它（第 8 轮）</b></p>
 *
 * <p>枪械模型里有两个语义不同的弹匣节点：</p>
 * <ul>
 *   <li>{@code magazine} —— 换弹时<b>跟着手走</b>的那一个；</li>
 *   <li>{@code additional_magazine} —— <b>留在枪身上</b>的那一个。</li>
 * </ul>
 *
 * <p>上游 1.21.1 的实现是在 {@code additional_magazine} 的变换下把 {@code magazine}
 * 的网格<b>再渲染一次</b>（同一份几何画两遍）。默认枪包的 {@code reload_tactical}、
 * {@code reload_empty}、{@code inspect} 等动画同时驱动这两个节点，正依赖该行为。</p>
 *
 * <p>第 2 轮我把该 provider 改成了 {@code return null}，误以为
 * "{@code magazine} 本来就在模型树里会被遍历到"。<b>那是错的</b> ——
 * 树里那份是"跟手"的，"留在枪上"的那份只能靠这里补画。
 * 症状即：换弹/空仓换弹时枪上的弹匣消失，只剩手里那个。</p>
 *
 * <p>之所以做成"标记接口 + 由快照遍历器原生处理"，而不是像上游那样返回一个
 * 会自己写顶点的 lambda：{@code BedrockRenderSnapshot} 是延迟提交的，
 * 镜像几何必须与枪身共用同一个 {@code RenderType} 和同一批 DrawCommand
 * 才能保证渲染顺序与材质正确；由遍历器统一处理最简单也最安全。
 * （另见：非 {@link IFunctionalSubmitter} 的 renderer 会被遍历器直接跳过整棵子树。）</p>
 */
@FunctionalInterface
public interface IMirrorGeometry extends IFunctionalRenderer {
    /**
     * @return 需要在当前节点变换下额外绘制的节点；返回 {@code null} 表示不绘制。
     */
    @Nullable
    BedrockPart getMirroredPart();

    /**
     * 本接口只在快照遍历器中被识别处理，不参与旧的 same-buffer 立即渲染路径。
     */
    @Override
    default void render(com.mojang.blaze3d.vertex.PoseStack poseStack,
                        com.mojang.blaze3d.vertex.VertexConsumer vertexBuffer,
                        net.minecraft.world.item.ItemDisplayContext transformType,
                        int light,
                        int overlay) {
        // Snapshot-only implementation.
    }
}
