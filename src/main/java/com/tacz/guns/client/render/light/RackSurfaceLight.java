package com.tacz.guns.client.render.light;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.render.FlashMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** The rack frame is a *chunk model*, not the block entity that submits its contents. Use
 * the selected multipart model, its real UV/alpha and dye (never the collision box as art). */
public final class RackSurfaceLight {
    private static final Direction[] SIDES = Direction.values();
    private record Face(BakedQuad quad, int color) {}

    public static void submit(WorldFlashFrame frame, BlockState state, BlockPos pos, int dye, Vec3 camera,
                              PoseStack pose, SubmitNodeCollector out) {
        if (!frame.admitRack(pos)) return;
        var parts = new ArrayList<BlockModelPart>();
        var model = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(state);
        model.collectParts(RandomSource.create(state.getSeed(pos)), parts);
        var batches = new HashMap<Identifier, List<Face>>();
        int remaining = 256;
        scan: for (var part : parts) for (int i = 0; i <= SIDES.length; i++) {
            for (var quad : part.getQuads(i == SIDES.length ? null : SIDES[i])) {
                if (--remaining < 0) break scan;
                if (quad.lightEmission() == 15) continue;
                var a=quad.position0(); var c=quad.position2();
                var p = new Vec3(pos.getX()+(a.x()+c.x())*.5, pos.getY()+(a.y()+c.y())*.5, pos.getZ()+(a.z()+c.z())*.5);
                var face = quad.direction();
                if ((camera.x-p.x)*face.getStepX()+(camera.y-p.y)*face.getStepY()+(camera.z-p.z)*face.getStepZ() <= 0) continue;
                var air = pos.relative(face);
                float ambient = 1 - .65f * frame.level.getMaxLocalRawBrightness(air) / 15f;
                int rgb = frame.reflectedColor(p, face, pos, ambient);
                if (rgb == 0) continue;
                int color = ARGB.multiply(0xff000000 | rgb, quad.isTinted() ? 0xff000000 | dye : -1);
                batches.computeIfAbsent(quad.sprite().atlasLocation(), k -> new ArrayList<>()).add(new Face(quad,color));
            }
        }
        for (var batch : batches.entrySet()) {
            var faces = batch.getValue();
            out.submitCustomGeometry(pose, FlashMaterials.surface(batch.getKey()), (entry, vertices) -> {
                for (var f : faces) {
                    var side=f.quad.direction(); var lifted=entry.copy();
                    lifted.pose().translate(.006f*side.getStepX(), .006f*side.getStepY(), .006f*side.getStepZ());
                    int c = f.color;
                    vertices.putBulkData(lifted, f.quad, ARGB.redFloat(c), ARGB.greenFloat(c), ARGB.blueFloat(c),
                            ARGB.alphaFloat(c), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                }
            });
        }
    }
    private RackSurfaceLight() {}
}
