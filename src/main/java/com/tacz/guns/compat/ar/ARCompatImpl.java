package com.tacz.guns.compat.ar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 26.2 迁移: acceleratedrendering mod 尚未移植到 26.2，所有方法改为 no-op。
 * TODO: 等待 acceleratedrendering 的 26.2 版本发布后恢复实现
 */
public class ARCompatImpl {

	public static boolean shouldAccelerate() {
		return false;
	}

	public static boolean isAccelerated(VertexConsumer vertexConsumer) {
		return false;
	}

	public static void setRenderingLevel() {
		// no-op
	}

	public static void resetRenderingLevel() {
		// no-op
	}

	public static void setRenderLayer(int layer) {
		// no-op
	}

	public static void setRenderBeforeFunction(Runnable runnable) {
		// no-op
	}

	public static void setRenderAfterFunction(Runnable runnable) {
		// no-op
	}

	public static void resetRenderLayer() {
		// no-op
	}

	public static void resetRenderBeforeFunction() {
		// no-op
	}

	public static void resetRenderAfterFunction() {
		// no-op
	}

	public static void disableAcceleration() {
		// no-op
	}

	public static void resetAcceleration() {
		// no-op
	}

	public static void renderLaser(
			VertexConsumer vertexConsumer,
			float z,
			float width,
			boolean fadeOut,
			PoseStack poseStack,
			int color
	) {
		// no-op: acceleratedrendering 不可用
	}
}
