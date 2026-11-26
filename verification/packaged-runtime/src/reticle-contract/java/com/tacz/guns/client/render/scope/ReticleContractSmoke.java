package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.model.bedrock.*;
import com.tacz.guns.client.renderer.snapshot.BedrockRenderSnapshot;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Test-mod-only contract, executed both by JUnit and in each intermediary optics client.
 * Cube.write records the actual captured matrix/light/alpha, not a parallel mock extractor. */
public final class ReticleContractSmoke {
    private record Seen(String id, int light, float alpha, Matrix4f pose) {}
    private record Layer(BedrockRenderSnapshot snapshot, boolean luminous) {}
    private static final class Part extends BedrockPart {
        Part(String name) { super(name); }
        Part child(Part child) { addChild(child); child.parent = this; return child; }
    }
    private static final class Cube implements BedrockCube {
        final String id;
        final List<Seen> seen;
        final BedrockCube geometry;
        Cube(String id, List<Seen> seen, float size) {
            this.id = id; this.seen = seen;
            geometry = new BedrockCubeBox(0, 0, 1, 1, 0, size, size, .01f, 0, false, 16, 16);
        }
        @Override public BedrockPolygon[] getPolygons() { return geometry.getPolygons(); }
        @Override public void compile(PoseStack.Pose pose, VertexConsumer out, int light, int overlay,
                                      float r, float g, float b, float alpha) {
            seen.add(new Seen(id, light, alpha, new Matrix4f(pose.pose())));
        }
    }

    public static void verify() {
        var seen = new ArrayList<Seen>();
        var root = new Part("root"); root.x = 16; root.visible = false;
        var division = root.child(new Part("division")); division.y = 32; division.visible = false;
        division.cubes.add(new Cube("ink", seen, 12)); // larger than the fallback heuristic
        var light = division.child(new Part("dot_illuminated")); light.illuminated = true; light.z = 48;
        light.cubes.add(new Cube("light", seen, 1));
        var nested = light.child(new Part("cross_illuminated")); nested.illuminated = true;
        nested.cubes.add(new Cube("nested", seen, 1));
        var inactive = root.child(new Part("division_2")); inactive.visible = false;
        inactive.cubes.add(new Cube("inactive", seen, 1));
        var nodes = new ScopeNodeSet(List.of(division), List.of(light, nested));
        var pose = new PoseStack(); pose.translate(4, 5, 6);
        var original = new Matrix4f(pose.last().pose());
        var layers = extract(pose, nodes, true, 1, 15728880);
        require(layers.size() == 2 && !layers.getFirst().luminous && layers.getLast().luminous,
                "Hybrid did not partition ink and luminous geometry");
        // Already full-lit ink must remain ink; using light == FULL_BRIGHT to split is incorrect.
        layers.forEach(layer -> layer.snapshot.write(null));
        require(ids(seen).equals(List.of("ink", "light", "nested")), "Duplicated or lost hybrid subtree: " + ids(seen));
        require(seen.getFirst().light == 15728880 && !layers.getFirst().luminous, "Sunlit ink became emissive");
        require(seen.getFirst().pose.equals(new Matrix4f().translation(5, 7, 6), .0001f), "Ancestor pose applied twice/lost");
        require(!root.visible && !division.visible && !inactive.visible && pose.last().pose().equals(original),
                "Capture changed shared visibility or caller pose");
        // Snapshots must survive later model animation/visibility mutations unchanged.
        division.x = 100; light.visible = false;
        seen.clear(); layers.forEach(layer -> layer.snapshot.write(null));
        require(seen.getFirst().pose.equals(new Matrix4f().translation(5, 7, 6), .0001f), "Deferred pose was mutable");
        division.x = 0; light.visible = true;
        seen.clear();
        var dark = extract(pose, nodes, true, .675f, 0);
        dark.forEach(layer -> layer.snapshot.write(null));
        require(seen.getFirst().light == 0 && seen.get(1).light == 15728880 && seen.get(2).light == 15728880,
                "Mixed material light inheritance lost");
        require(Math.abs(seen.getFirst().alpha - .5f) < .0001f, "ADS alpha changed");
        // Nested division roots must also be captured only once.
        seen.clear(); extract(pose, new ScopeNodeSet(List.of(division, light), List.of(light, nested)), true, 1, 0)
                .forEach(layer -> layer.snapshot.write(null));
        require(ids(seen).equals(List.of("ink", "light", "nested")), "Nested division root submitted twice");
        // No aperture: no ink and no huge luminous panels, but keep the small standalone mark.
        var standalone = new Part("reticle_illuminated"); standalone.illuminated = true;
        standalone.cubes.add(new Cube("panel", seen, 20)); standalone.cubes.add(new Cube("fallback", seen, 1));
        seen.clear(); extract(pose, new ScopeNodeSet(List.of(division), List.of(standalone)), false, 1, 0)
                .forEach(layer -> layer.snapshot.write(null));
        require(ids(seen).equals(List.of("fallback")), "Unsafe no-aperture fallback: " + ids(seen));
        require(extract(pose, nodes, true, .35f, 0).isEmpty(), "Reticle visible before ADS fade");
        require(extract(pose, new ScopeNodeSet(List.of(division), List.of()), false, 1, 0).isEmpty(),
                "Pure etched tree leaked without aperture");
        // Exceptional consumers must not leave a visible root or unbalanced pose stack either.
        boolean threw = false;
        try {
            ReticleGeometry.extract(context(pose, true, 1, 0), nodes, (snapshot, luminous) -> { throw new IllegalStateException("probe"); });
        } catch (IllegalStateException expected) { threw = true; }
        require(threw && !division.visible && pose.last().pose().equals(original), "Exceptional capture leaked state");
    }

    private static List<Layer> extract(PoseStack pose, ScopeNodeSet nodes, boolean mask, float aim, int light) {
        var result = new ArrayList<Layer>();
        ReticleGeometry.extract(context(pose, mask, aim, light), nodes, (snapshot, luminous) -> result.add(new Layer(snapshot, luminous)));
        return result;
    }
    private static IReticleRenderer.Context context(PoseStack pose, boolean mask, float aim, int light) {
        return new IReticleRenderer.Context(pose, null, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                null, null, light, 0, aim, mask, false, false);
    }
    private static List<String> ids(List<Seen> seen) { return seen.stream().map(Seen::id).toList(); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private ReticleContractSmoke() {}
}
