package com.tacz.guns.client.renderer.snapshot;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.BedrockPolygon;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

/** Conservative raster bounds for the final reticle decal. A subpixel quad may cover a
 * pixel without containing its centre. Expand only the *sampling domain* of small faces;
 * the fragment shader integrates samples inside the original unit quad and its original UVs.
 * No substitute dot, readback, enlarged artwork, or extra framebuffer. */
final class ReticleCoverage {
    private final Vector3f normal = new Vector3f();
    private final Vector3f origin = new Vector3f(), u = new Vector3f(), v = new Vector3f();
    private final Vector4f a = new Vector4f(), b = new Vector4f(), c = new Vector4f();

    static void write(List<BedrockRenderSnapshot.DrawCommand> commands, VertexConsumer out,
                      @Nullable Matrix4fc clip, int width, int height) {
        var writer = new ReticleCoverage();
        for (var draw : commands) for (var cube : draw.cubes()) for (var face : cube.getPolygons()) {
            if (face != null && face.isPresent()) writer.quad(draw, face, out, clip, width, height);
        }
    }

    private void quad(BedrockRenderSnapshot.DrawCommand draw, BedrockPolygon face, VertexConsumer out,
                      @Nullable Matrix4fc clip, int width, int height) {
        var corners = face.vertices;
        draw.pose().transformPosition(origin.set(corners[0].pos).mul(1f / 16));
        draw.pose().transformPosition(u.set(corners[1].pos).mul(1f / 16));
        draw.pose().transformPosition(v.set(corners[3].pos).mul(1f / 16));
        float padU = 0, padV = 0;
        if (clip != null) {
            a.set(origin, 1).mul(clip); b.set(u, 1).mul(clip); c.set(v, 1).mul(clip);
            if (a.w > 1e-5f && b.w > 1e-5f && c.w > 1e-5f) {
                float ux = (b.x / b.w - a.x / a.w) * width / 2, uy = (b.y / b.w - a.y / a.w) * height / 2;
                float vx = (c.x / c.w - a.x / a.w) * width / 2, vy = (c.y / c.w - a.y / a.w) * height / 2;
                float area = Math.abs(ux * vy - uy * vx);
                if (area > 1e-6f) {
                    // Half a pixel projected onto each face normal (L1 support of a pixel square).
                    padU = Math.min(1, (Math.abs(vx) + Math.abs(vy)) / (2 * area));
                    padV = Math.min(1, (Math.abs(ux) + Math.abs(uy)) / (2 * area));
                }
            }
        }
        u.sub(origin); v.sub(origin);
        draw.normal().transform(normal.set(face.normal));
        var uv0 = corners[0]; var uv1 = corners[1]; var uv3 = corners[3];
        for (int i = 0; i < 4; i++) {
            float s = i == 1 || i == 2 ? 1 + padU : -padU;
            float t = i >= 2 ? 1 + padV : -padV;
            out.addVertex(origin.x + s*u.x + t*v.x, origin.y + s*u.y + t*v.y, origin.z + s*u.z + t*v.z)
                    .setColor(draw.red(), draw.green(), draw.blue(), draw.alpha())
                    .setUv(uv0.u + s*(uv1.u-uv0.u) + t*(uv3.u-uv0.u), uv0.v + s*(uv1.v-uv0.v) + t*(uv3.v-uv0.v))
                    // Vanilla's 16x16 overlay uses only the low nibble of each UV1 lane.
                    // Store face coordinates in the upper bits, keep real light and overlay for etched ink.
                    .setUv1(Math.round((s+1)*512)*16 + (draw.overlay() & 15),
                            Math.round((t+1)*512)*16 + (draw.overlay() >>> 16 & 15))
                    .setLight(draw.light()).setNormal(normal.x, normal.y, normal.z);
        }
    }
}
