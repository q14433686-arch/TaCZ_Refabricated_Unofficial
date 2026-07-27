package com.tacz.guns.compat.ar;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 26.2 迁移: acceleratedrendering mod 尚未移植到 26.2，IAcceleratedRenderer 接口不存在。
 * 暂时移除接口实现，保留类结构。
 * TODO: 等待 acceleratedrendering 的 26.2 版本发布后恢复 implements IAcceleratedRenderer<BeamRenderContext>
 */
public class AcceleratedBeamRenderer {

	public static final AcceleratedBeamRenderer INSTANCE = new AcceleratedBeamRenderer();

	public void render(VertexConsumer vertexConsumer, BeamRenderContext context, Matrix4f transform, Matrix3f normal, int light, int overlay, int color) {
		// no-op: acceleratedrendering 不可用
	}
}
