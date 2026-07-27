package com.tacz.guns.client.model.bedrock;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

public interface BedrockCube {
    void compile(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float red, float green, float blue, float alpha);

    /**
     * 只读访问构成本立方体的六个面。
     *
     * <p><b>为什么放在接口上而不是某个实现类上：</b>
     * 两个实现 {@code BedrockCubeBox} 与 {@code BedrockCubePerFace} 都持有
     * 完全同构的 {@code BedrockPolygon[6]} 字段，只是 UV 来源不同
     * （前者按整体 uv 偏移推算，后者按 face_uv 逐面指定）。
     * 几何顶点坐标的算法两者完全一致。</p>
     *
     * <p>瞄具掩码（{@code ScopeMaskRenderer}）需要绕开 {@code VertexConsumer}
     * 自建顶点缓冲，因此不能用 {@link #compile}，但必须复用<b>完全相同</b>的顶点数据，
     * 否则掩码会与画面错位。</p>
     *
     * <p>第一版把访问器只加在 {@code BedrockCubeBox} 上并用 {@code instanceof} 过滤，
     * 结果实测目镜掩码全黑 —— 因为默认枪包 <b>161 个目镜立方体
     * 无一例外全是 {@code BedrockCubePerFace}</b>（它们都带 {@code face_uv}），
     * 被那个 {@code instanceof} 百分之百滤掉了。教训：不要用实现类做能力判断。</p>
     */
    BedrockPolygon[] getPolygons();
}
