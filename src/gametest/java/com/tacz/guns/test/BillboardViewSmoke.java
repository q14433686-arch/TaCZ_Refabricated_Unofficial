package com.tacz.guns.test;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.google.gson.JsonParser;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDeserializer;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.List;

/** Measure the oriented long axis of an upright billboard in the actual frame, not just the
 * quaternion that should have reached the renderer. The full-facing card is the scale control. */
final class BillboardViewSmoke {
    private static int roll;
    private static boolean registered;
    static void run(ClientGameTestContext ctx) {
        var facing = ctx.computeOnClient(mc -> new float[]{mc.player.getYRot(), mc.player.getXRot()});
        var rt = WorldEmitterManager.getInstance();
        try {
            ctx.runOnClient(mc -> {
                if (!registered) { registered = true; ViewportEvent.CAMERA.register(e -> { if (roll != 0) e.setRoll(e.getRoll()+roll); }); }
            });
            ctx.getInput().lookAt(0,-35);
            for (int angle : List.of(0,30)) {
                ctx.runOnClient(mc -> roll = angle); ctx.waitTicks(4);
                String prefix = "tacz_billboard_roll_" + angle + "_";
                var base = SnowstormMaterialSmoke.read(ctx.takeScreenshot(prefix + "control"));
                double reference = 0;
                for (var mode : List.of("rotate_xyz", "rotate_y", "lookat_y")) {
                    var up = ctx.computeOnClient(mc -> {
                        var json = JsonParser.parseString(SnowstormMaterialSmoke.PROBE.formatted("particles_opaque")).getAsJsonObject();
                        var appearance = json.getAsJsonObject("particle_effect").getAsJsonObject("components").getAsJsonObject("minecraft:particle_appearance_billboard");
                        appearance.add("size", JsonParser.parseString("[0.055,0.26]")); appearance.addProperty("facing_camera_mode",mode);
                        var def = new ParticleEffectDeserializer().parse(json, new ParticleMolangEnvironment());
                        var camera = mc.gameRenderer.mainCamera(); var at = camera.position().add(mc.player.getLookAngle().scale(2));
                        rt.addEmitter(mc.level,new Matrix4f().translation((float)at.x,(float)at.y,(float)at.z),Vec3.ZERO,def,"");
                        return new Vector3f(0,1,0).rotate(new Quaternionf(camera.rotation()).conjugate());
                    });
                    ctx.waitTicks(3);
                    var band = principalAxis(base,SnowstormMaterialSmoke.read(ctx.takeScreenshot(prefix + mode)));
                    double length = Math.hypot(up.x,up.y);
                    if (angle != 0 && Math.abs(up.x/length) < .4) throw new AssertionError("Camera roll was not applied");
                    boolean full = mode.equals("rotate_xyz");
                    double alignment = full ? Math.abs(band[1]) : Math.abs((band[0]*up.x - band[1]*up.y)/length);
                    if (alignment < .98) throw new AssertionError(prefix + mode + " long-axis alignment=" + alignment);
                    if (full) reference = band[2];
                    else if (Math.abs(band[2]/reference - length) > .09) throw new AssertionError(mode + " projected height=" + band[2]/reference + " vs " + length);
                    ctx.runOnClient(mc -> rt.clear()); ctx.waitTicks(2);
                }
            }
            GunMod.LOGGER.info("PORT_BILLBOARD: pitched -35 degrees, roll 0/30; both upright cards track projected world up and foreshorten, full-facing card stays vertical");
        } finally { ctx.runOnClient(mc -> { roll=0; rt.clear(); }); ctx.getInput().lookAt(facing[0], facing[1]); }
    }

    private static double[] principalAxis(BufferedImage base, BufferedImage im) {
        double xx=0,yy=0,xy=0,sx=0,sy=0; int n=0, cx=im.getWidth()/2, cy=im.getHeight()/2;
        for(int y=-80;y<80;y++) for(int x=-80;x<80;x++) {
            int c=im.getRGB(cx+x,cy+y), old=base.getRGB(cx+x,cy+y), g=c>>8&255;
            int chroma = Math.min(c>>16&255,c&255)-g;
            int background = Math.min(old>>16&255,old&255)-(old>>8&255);
            if (chroma < 35 || chroma-background < 35) continue;
            sx+=x; sy+=y; xx+=x*x; yy+=y*y; xy+=x*y; n++;
        }
        if(n<100) throw new AssertionError("Not enough colored billboard pixels: "+n);
        xx=xx/n-sx*sx/(n*(double)n); yy=yy/n-sy*sy/(n*(double)n); xy=xy/n-sx*sy/(n*(double)n);
        double axis=.5*Math.atan2(2*xy,xx-yy);
        return new double[]{Math.cos(axis), Math.sin(axis), Math.sqrt((xx+yy+Math.hypot(xx-yy,2*xy))/2)};
    }
}
