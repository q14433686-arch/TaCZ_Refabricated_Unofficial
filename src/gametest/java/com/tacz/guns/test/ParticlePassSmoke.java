package com.tacz.guns.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleDescription;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleDescription.Material;
import com.tacz.guns.client.particle.bedrock.particle.world.SnowstormMaterials;
import com.tacz.guns.client.render.scope.ScopeBodyRenderTypes;
import com.tacz.guns.mixin.client.ThermalRenderTypeAccessor;
import com.tacz.guns.test.mixin.MaterialTransformObserver;
import com.tacz.guns.util.IlluminatedLights;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.image.BufferedImage;

/** Calibration of the actual particle materials, orthogonal to emitter lifecycle/locator tests.
 * Draw the front blue sample first, then a green sample farther away. An empty/front-only/rear-only
 * measurement at this same pixel rules out culling or a dark texture masquerading as a depth write. */
final class ParticlePassSmoke {
    private static final Identifier TEX = Identifier.parse("port_test:textures/gun/rack_mesh.png");
    private static final int FULL = IlluminatedLights.FULL_BRIGHT;
    private record Scene(Material front, int light, boolean rear) {}
    private record Signal(double green, double blue) {}
    private static Scene scene;
    private static Vec3 origin;
    private static boolean registered;
    private static int submits;

    private static RenderType type(Material material, boolean clipped, boolean hand) {
        return SnowstormMaterials.type(new ParticleDescription(TEX, material, TEX, 4, 4), clipped, hand);
    }

    private static void uv(RenderType type, float offset) {
        var setup = ((ThermalRenderTypeAccessor)(Object)type).tacz$thermalSetup();
        var transform = ((MaterialTransformObserver)(Object)setup).taczTest$textureTransform();
        Matrix4f matrix = transform.createMatrix();
        if (!matrix.equals(new Matrix4f().translation(offset,offset,0), 1e-5f))
            throw new AssertionError("Wrong UV matrix for " + type + ": " + matrix);
    }

    private static void contract() {
        for (int pass = 0; pass < 3; pass++) {
            boolean clipped = pass == 2, hand = pass != 0;
            var add = type(Material.PARTICLES_ADD, clipped, hand);
            var emissive = type(Material.PARTICLES_EMISSIVE, clipped, hand);
            if (!new DepthStencilState(DepthStencilState.DEFAULT.depthTest(), false).equals(add.pipeline().getDepthStencilState())
                    || !DepthStencilState.DEFAULT.equals(emissive.pipeline().getDepthStencilState()))
                throw new AssertionError("Depth test/write for pass " + pass + ": " + add.pipeline().getDepthStencilState() + " / " + emissive.pipeline().getDepthStencilState());
            uv(add, 0); uv(emissive, 0); uv(type(Material.ENERGY_SWIRL, clipped, hand), 1);
        }
        uv(ScopeBodyRenderTypes.flashSwirlClipped(TEX), 0); // native muzzle, not SBM's swirl
    }

    private static void quad(OrderedSubmitNodeCollector collector, Vec3 p, RenderType type, int light, boolean green) {
        var pose = new PoseStack(); pose.translate(p.x,p.y,p.z);
        collector.submitCustomGeometry(pose, type, (entry,out) -> {
            float s = green ? .18f : .27f;
            vertex(out, entry.pose(), -s,-s,0,1,light,green); vertex(out, entry.pose(), -s,s,0,0,light,green);
            vertex(out, entry.pose(), s,s,1,0,light,green); vertex(out, entry.pose(), s,-s,1,1,light,green);
        });
    }
    private static void vertex(VertexConsumer out, Matrix4f pose, float x, float y, float u, float v, int light, boolean green) {
        out.addVertex(pose,x,y,0).setColor(0,green ? 255 : 0,green ? 0 : 255,255).setUv(u,v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0,1,0);
    }

    static void run(ClientGameTestContext ctx, BufferedImage wall) {
        try {
            ctx.runOnClient(mc -> {
                contract();
                origin = mc.gameRenderer.getMainCamera().position(); // coal wall is directly to the south
                if (registered) return;
                registered = true;
                LevelRenderEvents.COLLECT_SUBMITS.register(c -> {
                    if (scene == null) return;
                    var p = origin.subtract(c.levelState().cameraRenderState.pos);
                    if (scene.front != null) quad(c.submitNodeCollector().order(7), p.add(0,0,1.7), type(scene.front,false,false), scene.light, false);
                    if (scene.rear) quad(c.submitNodeCollector().order(8), p.add(0,0,2.3), type(Material.PARTICLES_ADD,false,false), FULL, true);
                    submits++;
                });
            });
            var rear = sample(ctx,wall,"rear",null,FULL,true);
            var a = Material.PARTICLES_ADD; var e = Material.PARTICLES_EMISSIVE;
            var add = sample(ctx,wall,"add_full",a,FULL,false); var em = sample(ctx,wall,"emissive_full",e,FULL,false);
            var darkAdd = sample(ctx,wall,"add_dark",a,0,false); var darkEm = sample(ctx,wall,"emissive_dark",e,0,false);
            var addRear = sample(ctx,wall,"add_rear",a,FULL,true); var emRear = sample(ctx,wall,"emissive_rear",e,FULL,true);
            boolean iris = Boolean.getBoolean("tacz.test.iris");
            if (rear.green < 40 || Math.min(add.blue,em.blue) < 35 || addRear.blue < .8*add.blue || emRear.blue < .8*em.blue
                    || addRear.green < .8*rear.green || emRear.green > .1*rear.green)
                throw new AssertionError("Particle depth: rear=" + rear + ", fronts=" + add + "/" + em + ", both=" + addRear + "/" + emRear);
            // This Iris regression shader passes vertex colour through, ignoring the lightmap.
            if ((!iris && darkAdd.blue > add.blue - 25) || Math.abs(darkEm.blue - em.blue) > 8)
                throw new AssertionError("Particle light: add=" + darkAdd + "/" + add + ", emissive=" + darkEm + "/" + em);
            ctx.runOnClient(mc -> contract()); // fixed UV translations, not a time-driven animation
            GunMod.LOGGER.info("PORT_PARTICLE_PASS: rear G={} over add/emissive={}/{}; dark/full B add={}/{}, emissive={}/{}; three UV/depth setups passed",
                    Math.round(rear.green), Math.round(addRear.green), Math.round(emRear.green), Math.round(darkAdd.blue), Math.round(add.blue), Math.round(darkEm.blue), Math.round(em.blue));
        } finally { ctx.runOnClient(mc -> { scene = null; origin = null; }); }
    }

    private static Signal sample(ClientGameTestContext ctx, BufferedImage wall, String name, Material front, int light, boolean rear) {
        ctx.runOnClient(mc -> { scene = new Scene(front,light,rear); submits = 0; });
        ctx.waitFor(mc -> submits >= 3);
        var image = SnowstormMaterialSmoke.read(ctx.takeScreenshot("tacz_particle_pass_" + name));
        int cx = image.getWidth()/2, cy = image.getHeight()/2, g = 0, b = 0, n = 0;
        for (int y=-16; y<=16; y++) for (int x=-16; x<=16; x++) {
            if (Math.abs(x)<7 && Math.abs(y)<7) continue;
            int v=image.getRGB(cx+x,cy+y), w=wall.getRGB(cx+x,cy+y);
            g += (v >> 8 & 255) - (w >> 8 & 255); b += (v & 255) - (w & 255); n++;
        }
        return new Signal(g/(double)n,b/(double)n);
    }
}
