package com.tacz.guns.compat.ar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.loader.api.FabricLoader;

public class ARCompat {

	public static final String MOD_ID = "acceleratedrendering";

	public static boolean LOADED;

	public static void init() {
		LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);
		// 26.2 迁移: 加速渲染 Mod 可能不兼容 26.2 Feature Rendering，暂时禁用
		// 若检测到 26.2 且 Mod 版本不支持，强制禁用
		// TODO: 等待 acceleratedrendering 的 26.2 版本发布后重新启用
		if (LOADED) {
			// 简单检查: 若当前是 26.2，默认禁用
			LOADED = false;
		}
	}

	public static boolean shouldAccelerate() {
		// 26.2 暂时禁用
		return false;
		// return LOADED && ARCompatImpl.shouldAccelerate();
	}

	public static boolean isAccelerated(VertexConsumer vertexConsumer) {
		// 26.2: ARCompatImpl not available, acceleration disabled
		return false;
	}

	public static void setRenderingLevel() {
		// 26.2: no-op
	}

	public static void resetRenderingLevel() {
		// 26.2: no-op
	}

	public static void setRenderLayer(int layer) {
		// 26.2: no-op
	}

	public static void setRenderBeforeFunction(Runnable runnable) {
		// 26.2: no-op
	}

	public static void setRenderAfterFunction(Runnable runnable) {
		// 26.2: no-op
	}

	public static void resetRenderLayer() {
		// 26.2: no-op
	}

	public static void resetRenderBeforeFunction() {
		// 26.2: no-op
	}

	public static void resetRenderAfterFunction() {
		// 26.2: no-op
	}

	public static void disableAcceleration() {
		// 26.2: no-op
	}

	public static void resetAcceleration() {
		// 26.2: no-op
	}

	// 防止类意外加载 (直接在BeamRenderer类使用AcceleratedBeamRenderer.INSTANCE在会触发类加载)
	public static void renderLaser(
			VertexConsumer extension,
			float z,
			float width,
			boolean fadeOut,
			PoseStack poseStack,
			int color
	) {
		// 26.2: no-op, accelerated rendering disabled
	}
}