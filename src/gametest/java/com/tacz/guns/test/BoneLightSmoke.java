package com.tacz.guns.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.util.IlluminatedLights;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

import java.awt.image.BufferedImage;

/** The only difference between the two solids is a parent bone's suffix. Even the negative
 * control gets FULL_BRIGHT, so passing cannot be explained by a brighter lightmap column.
 * World collector, shader and colour buffer are real; no reticle renderer is involved. */
final class BoneLightSmoke {
    private static final Identifier TEXTURE = Identifier.parse("port_test:textures/gun/rack_mesh.png");
    private static boolean registered, translucent;
    private static BedrockModel drawing;
    private static Vec3 position;
    private static int angle, submits;

    private static BedrockModel model(String parent) {
        var data = ClientAssetsManager.GSON.fromJson("""
                {"format_version":"1.12.0","minecraft:geometry":[{"description":{
                 "identifier":"geometry.face_light","texture_width":32,"texture_height":32,
                 "visible_bounds_width":1,"visible_bounds_height":1,"visible_bounds_offset":[0,0,0]},
                 "bones":[{"name":"%1$s","pivot":[0,24,0]},
                 {"name":"plain_child","parent":"%1$s","pivot":[0,24,0],
                  "cubes":[{"origin":[-4,20,-4],"size":[8,8,8],"uv":[0,0]}]}]}]}
                """.formatted(parent), BedrockModelPOJO.class);
        return new BedrockModel(data, BedrockVersion.NEW);
    }

    private static void register() {
        if (registered) return;
        registered = true;
        LevelRenderEvents.COLLECT_SUBMITS.register(view -> {
            if (drawing == null) return;
            var p = position.subtract(view.levelState().cameraRenderState.pos);
            var pose = new PoseStack();
            pose.translate(p.x,p.y,p.z);
            pose.rotate(Axis.YP.rotationDegrees(angle)); pose.rotate(Axis.XP.rotationDegrees(28));
            if (translucent) pose.scale(-1, 1, 1); // reverse winding; texture/vertex alpha stay opaque
            var body = translucent ? RenderTypes.entityTranslucent(TEXTURE) : RenderTypes.entityCutout(TEXTURE);
            drawing.submit(pose, ItemDisplayContext.FIXED, view.submitNodeCollector(), body,
                    IlluminatedLights.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            submits++;
        });
    }

    static void run(ClientGameTestContext ctx, BufferedImage wall) {
        var plain = model("lamp"); var glow = model("lamp_illuminated");
        boolean iris = Boolean.getBoolean("tacz.test.iris");
        try {
            ctx.runOnClient(mc -> {
                register();
                position = mc.gameRenderer.mainCamera().position().add(mc.player.getLookAngle().scale(2.1));
            });
            for (boolean mirror : new boolean[]{false, true}) {
                String pass = mirror ? "translucent_mirror" : "cutout";
                for (int yaw : new int[]{35, 125}) {
                    int[] a = sample(ctx, wall, plain, yaw, mirror, "plain");
                    int[] b = sample(ctx, wall, glow, yaw, mirror, "glow");
                    // The authored Iris shader passes vertex colour through. Vanilla's negative
                    // control MUST actually vary; both paths must preserve the visible cube.
                    if ((!iris && a[1] - a[0] < 12) || b[1] - b[0] > 5 || b[0] < a[1] - 5
                            || Math.abs(b[2] - a[2]) > .15 * a[2]) {
                        throw new AssertionError("Face light " + pass + " " + yaw + ": plain="
                                + java.util.Arrays.toString(a) + ", glowing=" + java.util.Arrays.toString(b));
                    }
                    GunMod.LOGGER.info("PORT_BONE_LIGHT: {} yaw={} plain G10/90={}/{}, illuminated child={}/{} ({} / {} pixels); iris={}",
                            pass, yaw, a[0], a[1], b[0], b[1], a[2], b[2], iris);
                }
            }
        } finally { ctx.runOnClient(mc -> { drawing = null; position = null; }); }
    }

    private static int[] sample(ClientGameTestContext ctx, BufferedImage base, BedrockModel model, int yaw, boolean mirror, String name) {
        ctx.runOnClient(mc -> { translucent = mirror; angle = yaw; drawing = model; submits = 0; });
        ctx.waitFor(mc -> submits >= 3);
        var frame = SnowstormMaterialSmoke.read(ctx.takeScreenshot("tacz_bone_light_" + (mirror ? "mirror_" : "") + yaw + "_" + name));
        int[] histogram = new int[256]; int count = 0;
        int cx = frame.getWidth()/2, cy = frame.getHeight()/2;
        for (int y = cy-65; y < cy+65; y++) for (int x = cx-65; x < cx+65; x++) {
            int c = frame.getRGB(x,y), g = c >> 8 & 255, r = c >> 16 & 255;
            if (g - r > 45 && (c & 255) - r > 35 && g - (base.getRGB(x,y) >> 8 & 255) > 35) {
                histogram[g]++; count++;
            }
        }
        if (count < 500) throw new AssertionError("Cannot see the " + name + " light probe: " + count);
        int low = 0, high = 0, sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += histogram[i];
            if (sum < count * .1) low = i + 1;
            if (sum >= count * .9) { high = i; break; }
        }
        return new int[]{low,high,count};
    }
}
