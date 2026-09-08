package cn.sh1rocu.tacz.compat.meshloader.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Standalone regression test, no Minecraft/GL/JUnit needed. Models the audited Iris lifecycle:
 * setup on the first draw, retain normal/inverse-MV + PBR state, clear on pass close.
 * This verifies batch policy against that contract, not actual shader-pack output.
 */
public final class MeshRenderPassBatchesTest {
    private record Bone(double yaw, String texture) {
    }

    private record Result(int passes, int staleNormals, int staleAlbedo, List<Bone> drawn) {
    }

    public static void main(String[] args) {
        check(MeshRenderPassBatches.partition(List.of(), true).isEmpty(), "empty Iris frame");
        check(MeshRenderPassBatches.partition(List.of(), false).isEmpty(), "empty vanilla frame");

        // Two bones share a texture but rotate independently during inspection. A third uses
        // another material. Splitting only on texture would still leave the second normal stale.
        List<Bone> bones = List.of(new Bone(0, "body"), new Bone(Math.PI / 2, "body"),
                new Bone(-Math.PI / 4, "magazine"));
        Result old = render(List.of(bones));
        check(old.staleNormals() == 2, "fixture must reproduce stale normals in the old shared pass");
        check(old.staleAlbedo() == 1, "fixture must reproduce the missed PBR texture notification");
        Result textureOnly = render(List.of(bones.subList(0, 2), bones.subList(2, 3)));
        check(textureOnly.staleNormals() == 1, "per-texture splitting alone must not pass");

        Result fixed = render(MeshRenderPassBatches.partition(bones, true));
        check(fixed.passes() == bones.size(), "Iris must open a pass per bone");
        check(fixed.staleNormals() == 0 && fixed.staleAlbedo() == 0, "fresh Iris state per bone");
        check(fixed.drawn().equals(bones), "draw order and count must be preserved");

        List<List<Bone>> vanilla = MeshRenderPassBatches.partition(bones, false);
        check(vanilla.size() == 1 && vanilla.getFirst() == bones, "vanilla remains one batch");
        List<Bone> single = List.of(bones.getFirst());
        check(render(MeshRenderPassBatches.partition(single, true)).passes() == 1, "single bone");

        // Reuse the draw list across animation frames; no cached partition may retain old poses.
        for (int degrees = 0; degrees < 360; degrees += 15) {
            double angle = Math.toRadians(degrees);
            List<Bone> frame = List.of(new Bone(angle, "body"), new Bone(angle + 0.8, "body"),
                    new Bone(-angle, "magazine"));
            Result result = render(MeshRenderPassBatches.partition(frame, true));
            check(result.staleNormals() == 0 && result.staleAlbedo() == 0, "inspection at " + degrees);
            check(result.drawn().equals(frame), "frame order at " + degrees);
        }
        System.out.println("PASS: Iris per-bone normal/PBR state, 24 inspection poses, vanilla batching, empty/single draws");
    }

    private static Result render(List<List<Bone>> batches) {
        int passes = 0;
        int staleNormals = 0;
        int staleAlbedo = 0;
        List<Bone> drawn = new ArrayList<>();
        for (List<Bone> batch : batches) {
            passes++;
            // ExtendedShader.iris$clearState at finishRenderPass resets the setup flag.
            boolean isSetUp = false;
            double normalX = 0;
            double normalZ = 0;
            String albedo = null;
            for (Bone bone : batch) {
                // For a rigid Y rotation the inverse transpose maps (0,0,1) to (sin,0,cos).
                // Pushing a new MV alone does not invalidate Iris' once-per-pass setup flag.
                if (!isSetUp) {
                    normalX = Math.sin(bone.yaw());
                    normalZ = Math.cos(bone.yaw());
                    albedo = bone.texture();
                    isSetUp = true;
                }
                if (Math.abs(normalX - Math.sin(bone.yaw())) > 1e-9
                        || Math.abs(normalZ - Math.cos(bone.yaw())) > 1e-9) {
                    staleNormals++;
                }
                if (!bone.texture().equals(albedo)) {
                    staleAlbedo++;
                }
                drawn.add(bone);
            }
        }
        return new Result(passes, staleNormals, staleAlbedo, drawn);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
