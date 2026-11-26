package com.tacz.guns.client.renderer.crosshair;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/** A translucent elliptical annulus is a single GUI mesh, not overlapping dots or a stretched stock PNG. */
record RingBand(Matrix3x2f pose, float ix, float iy, float ox, float oy, int color,
                @Nullable ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
    private static final int N = 128;
    private static final float[] COS = new float[N], SIN = new float[N];
    static {
        for (int i = 0; i < N; i++) {
            double a = i * (2 * Math.PI / N);
            COS[i] = (float) Math.cos(a); SIN[i] = (float) Math.sin(a);
        }
    }

    static void draw(GuiGraphics gui, float ix, float iy, float ox, float oy, int color) {
        if (ox <= 0 || oy <= 0) return;
        var pose = new Matrix3x2f(gui.pose());
        int x = (int) Math.ceil(ox), y = (int) Math.ceil(oy);
        var bounds = new ScreenRectangle(-x, -y, 2*x, 2*y).transformMaxBounds(pose);
        var scissor = gui.scissorStack.peek();
        if (scissor != null) bounds = bounds.intersection(scissor);
        if (bounds != null) gui.guiRenderState.submitGuiElement(new RingBand(pose, ix, iy, ox, oy, color, scissor, bounds));
    }

    @Override public void buildVertices(VertexConsumer vertices) {
        float radius = Math.max(ox, oy);
        int step = radius > 40 ? 1 : radius > 12 ? 2 : 4;
        for (int i = 0; i < N; i += step) {
            int j = (i + step) % N;
            vertices.addVertexWith2DPose(pose, ix * COS[i], iy * SIN[i]).setColor(color);
            vertices.addVertexWith2DPose(pose, ix * COS[j], iy * SIN[j]).setColor(color);
            vertices.addVertexWith2DPose(pose, ox * COS[j], oy * SIN[j]).setColor(color);
            vertices.addVertexWith2DPose(pose, ox * COS[i], oy * SIN[i]).setColor(color);
        }
    }

    @Override public RenderPipeline pipeline() { return RenderPipelines.GUI; }
    @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
}
