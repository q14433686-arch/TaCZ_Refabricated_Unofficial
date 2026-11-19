package com.tacz.guns.test;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDeserializer;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import com.tacz.guns.compat.firstperson.FirstPersonAnimationCompat;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.List;

/** Measure the upright particle against a full-facing card in the same rendered frame. */
final class BillboardViewSmoke {
    private static Integer roll;
    private static boolean registered;

    static void run(ClientGameTestContext ctx) {
        var facing = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot()});
        var rt = WorldEmitterManager.getInstance();
        try {
            ctx.runOnClient(mc -> {
                if (!registered) {
                    registered = true;
                    ViewportEvent.CAMERA.register(e -> {
                        if (roll == null) return;
                        e.setYaw(0); e.setPitch(-35); e.setRoll(roll);
                    });
                }
            });
            ctx.getInput().lookAt(0, -35);
            for (int angle : List.of(0, 30)) {
                ctx.runOnClient(mc -> { roll = angle; rt.clear(); });
                ctx.waitTicks(4);
                // HandMaterialSmoke emptied the inventory, but the render stack intentionally
                // keeps its gun AND arms through put-away. They can occlude half of a test card.
                ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty()
                        && FirstPersonAnimationCompat.getMainRenderStack(mc.player).isEmpty());
                String prefix = "tacz_billboard_roll_" + angle + "_";
                var base = SnowstormMaterialSmoke.read(ctx.takeScreenshot(prefix + "control"));
                for (String mode : List.of("rotate_y", "lookat_y")) {
                    long before = ctx.computeOnClient(mc -> rt.rendered());
                    var up = ctx.computeOnClient(mc -> {
                        // Both cards have identical size, material and view-space Z. Keep the
                        // upright probe on the optical axis: perspective there scales Y by cos(pitch).
                        addCard(mc, "rotate_xyz", -.32f, true);
                        addCard(mc, mode, 0, false);
                        return new Vector3f(0, 1, 0).rotate(new Quaternionf(mc.gameRenderer.mainCamera().rotation()).conjugate());
                    });
                    ctx.waitFor(mc -> rt.particleCount() == 2 && rt.rendered() >= before + 2);
                    var frame = SnowstormMaterialSmoke.read(ctx.takeScreenshot(prefix + mode + "_pair"));
                    var reference = principalAxis(base, frame, true);
                    var band = principalAxis(base, frame, false);
                    double length = Math.hypot(up.x, up.y);
                    if (Math.abs(length - Math.cos(Math.toRadians(35))) > .01 || (angle != 0 && Math.abs(up.x / length) < .4))
                        throw new AssertionError("Camera pitch/roll was not applied: " + up);
                    double alignment = Math.abs((band[0] * up.x - band[1] * up.y) / length);
                    if (Math.abs(reference[1]) < .98 || alignment < .98)
                        throw new AssertionError(prefix + mode + " reference vertical=" + reference[1] + ", upright alignment=" + alignment);
                    double ratio = band[2] / reference[2];
                    if (Math.abs(ratio - length) > .09)
                        throw new AssertionError(prefix + mode + " same-frame height=" + ratio + " vs " + length);
                    ctx.runOnClient(mc -> rt.clear()); ctx.waitTicks(2);
                }
            }
            GunMod.LOGGER.info("PORT_BILLBOARD: pitched -35 degrees, roll 0/30; upright cards foreshorten against simultaneous full-facing controls");
        } finally {
            ctx.runOnClient(mc -> { roll = null; rt.clear(); });
            ctx.getInput().lookAt(facing[0], facing[1]);
        }
    }

    private static void addCard(Minecraft mc, String mode, float viewX, boolean reference) {
        var json = JsonParser.parseString(SnowstormMaterialSmoke.PROBE.formatted("particles_opaque")).getAsJsonObject();
        var parts = json.getAsJsonObject("particle_effect").getAsJsonObject("components");
        var card = parts.getAsJsonObject("minecraft:particle_appearance_billboard");
        card.add("size", JsonParser.parseString("[0.0275,0.13]"));
        card.addProperty("facing_camera_mode", mode);
        if (reference) parts.getAsJsonObject("minecraft:particle_appearance_tinting").add("color", JsonParser.parseString("[0.1,1,0.1,1]"));
        var def = new ParticleEffectDeserializer().parse(json, new ParticleMolangEnvironment());
        var camera = mc.gameRenderer.mainCamera();
        var offset = new Vector3f(viewX, 0, -1).rotate(camera.rotation());
        WorldEmitterManager.getInstance().addEmitter(mc.level, camera.position().add(offset.x, offset.y, offset.z), Vec3.ZERO, def);
    }

    private static int chroma(int c, boolean green) {
        int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
        return green ? g - Math.max(r, b) : Math.min(r, b) - g;
    }

    private static double[] principalAxis(BufferedImage base, BufferedImage im, boolean reference) {
        double xx=0,yy=0,xy=0,sx=0,sy=0; int n=0, cx=im.getWidth()/2, cy=im.getHeight()/2;
        int h = im.getHeight()/6;
        for (int y=-h; y<h; y++) for (int x=reference ? -Math.min(cx,3*h) : -h; x<(reference ? -h/2 : h); x++) {
            int value = chroma(im.getRGB(cx+x, cy+y), reference);
            if (value < 35 || value - chroma(base.getRGB(cx+x, cy+y), reference) < 35) continue;
            sx+=x; sy+=y; xx+=x*x; yy+=y*y; xy+=x*y; n++;
        }
        if(n<100) throw new AssertionError("Not enough " + (reference ? "reference" : "upright") + " billboard pixels: " + n);
        xx=xx/n-sx*sx/(n*(double)n); yy=yy/n-sy*sy/(n*(double)n); xy=xy/n-sx*sy/(n*(double)n);
        double axis=.5*Math.atan2(2*xy,xx-yy);
        return new double[]{Math.cos(axis), Math.sin(axis), Math.sqrt((xx+yy+Math.hypot(xx-yy,2*xy))/2)};
    }
}
