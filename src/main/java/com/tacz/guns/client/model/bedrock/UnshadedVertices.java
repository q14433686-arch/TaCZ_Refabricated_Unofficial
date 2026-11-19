package com.tacz.guns.client.model.bedrock;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3fc;

/** Full lightmap coordinates do not bypass the entity shader's normal-based diffuse term.
 * Match the 1.21.1 Fabric port: emit a light-facing normal in final vertex space, keeping
 * the original geometry, packed light (including real sky), blend, depth and material. */
public final class UnshadedVertices {
    public record Normal(float x, float y, float z) {
        public static final Normal UP = new Normal(0, 1, 0);
        public static Normal fromLight(Vector3fc primary) {
            float max = Math.max(Math.abs(primary.x()), Math.max(Math.abs(primary.y()), Math.abs(primary.z())));
            // Normal attributes are signed normalized bytes. Scaling by the largest component
            // stays in their range and lets minecraft_mix_light reach its brightness clamp.
            return max > 1e-6f && Float.isFinite(max)
                    ? new Normal(primary.x() / max, primary.y() / max, primary.z() / max) : UP;
        }
    }

    // CPU copy of the light entry most recently selected by vanilla. No GPU readback;
    // the wrapper takes its own immutable value when the vertices are actually emitted.
    private static volatile Normal selected = Normal.UP;
    public static void select(Normal value) { selected = value == null ? Normal.UP : value; }

    public static VertexConsumer wrap(VertexConsumer out) {
        return out instanceof Flat ? out : new Flat(out, selected);
    }

    private record Flat(VertexConsumer out, Normal n) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { out.addVertex(x,y,z); return this; }
        @Override public VertexConsumer setColor(int argb) { out.setColor(argb); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { out.setColor(r,g,b,a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { out.setUv(u,v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { out.setUv1(u,v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { out.setUv2(u,v); return this; }
        @Override public VertexConsumer setUv3(float u, float v) { out.setUv3(u,v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { out.setNormal(n.x,n.y,n.z); return this; }
        @Override public VertexConsumer setLineWidth(float width) { out.setLineWidth(width); return this; }
    }

    private UnshadedVertices() {}
}
