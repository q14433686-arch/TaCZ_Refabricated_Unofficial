package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.render.scope.ScopeMaskRenderer;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;

/** User-reported AMG10 regression: exercise all actual aiming locators, not just installation. */
final class ScopeUserRegressionSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        var scope=Identifier.parse("tacz:scope_amg_10");
        var saved=world.getServer().computeOnServer(s->world.getConnection().getServerPlayer().getMainHandItem().copy());
        var prior=ctx.computeOnClient(mc->new boolean[]{mc.options.bobView().get(),RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get()});
        StringBuilder errors=new StringBuilder();
        try {
            ctx.runOnClient(mc->{mc.options.bobView().set(false);RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(true);});
            for(int zoom=0;zoom<3;zoom++) {
                final int z=zoom;
                ctx.getInput().releaseKey(AimKey.AIM_KEY);
                world.getServer().runOnServer(s->{
                    var stack=GunItemBuilder.create().setId(Identifier.parse("tacz:m700"))
                            .putAttachment(AttachmentType.SCOPE,scope).setAmmoCount(3).build();
                    var gun=(IGun)stack.getItem();var tag=gun.getAttachmentTag(stack,AttachmentType.SCOPE);
                    AttachmentItemDataAccessor.setZoomNumberToTag(tag,z);gun.setAttachmentTag(stack,AttachmentType.SCOPE,tag);
                    world.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND,stack);
                });
                ctx.waitFor(mc->mc.player.getMainHandItem().getItem() instanceof IGun g
                        &&scope.equals(g.getAttachmentId(mc.player.getMainHandItem(),AttachmentType.SCOPE))
                        &&AttachmentItemDataAccessor.getZoomNumberFromTag(g.getAttachmentTag(mc.player.getMainHandItem(),AttachmentType.SCOPE))==z);
                ctx.waitTicks(30);ctx.getInput().holdKey(AimKey.AIM_KEY);ctx.waitTicks(45);
                ctx.takeScreenshot("tacz_amg10_zoom_"+z);
                String failure=ctx.computeOnClient(mc->{
                    if(!ScopeMaskRenderer.hasMaskThisFrame()||!ScopeMaskRenderer.hasMaskBounds())return "missing mask";
                    var b=ScopeMaskRenderer.maskBoundsNdc();
                    float w=(b[2]-b[0])*mc.getWindow().getWidth(),h=(b[3]-b[1])*mc.getWindow().getHeight();
                    GunMod.LOGGER.info("PORT_AMG10: zoom={} mask={}x{} centre={},{}",z,w,h,(b[0]+b[2])/2,(b[1]+b[3])/2);
                    return !(w>10&&h>10&&h/w>.35&&h/w<2&&Math.abs((b[1]+b[3])/2)<.6)
                            ?"invalid aperture "+w+"x"+h+" at "+(b[1]+b[3])/2:"";
                });
                if(!failure.isEmpty())errors.append("zoom ").append(z).append(": ").append(failure).append("; ");
                checkOwnedParticle(ctx,z);
            }
            if(!errors.isEmpty())throw new AssertionError("AMG10 optical-axis mismatch: "+errors);
            GunMod.LOGGER.info("PORT_AMG10: all three zooms produced bounded on-axis apertures");
        } finally {
            ctx.getInput().releaseKey(AimKey.AIM_KEY);
            ctx.runOnClient(mc->{mc.options.bobView().set(prior[0]);RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(prior[1]);});
            world.getServer().runOnServer(s->world.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND,saved));
            ctx.waitTicks(25);
        }
    }

    private static void checkOwnedParticle(ClientGameTestContext ctx,int zoom) {
        var runtime=com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager.getInstance();
        final java.awt.image.BufferedImage baseline;
        try { baseline=javax.imageio.ImageIO.read(ctx.takeScreenshot("tacz_amg10_particle_baseline_"+zoom).toFile()); }
        catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        ctx.runOnClient(mc->{
            var env=new com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment();
            var def=new com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDeserializer().parse(
                    com.google.gson.JsonParser.parseString("""
                    {"particle_effect":{"description":{"identifier":"port_test:scope_particle","basic_render_parameters":{
                    "material":"particles_add","texture":"minecraft:textures/particle/generic_0"}},"components":{
                    "sbm:fp_emitter_local_space":{"position":false,"rotation":false,"velocity":false,"to_world":true},
                    "minecraft:emitter_rate_instant":{"num_particles":1},"minecraft:emitter_lifetime_once":{"active_time":0.1},
                    "minecraft:emitter_shape_point":{"offset":[0,0,0]},"minecraft:particle_lifetime_expression":{"max_lifetime":1},
                    "minecraft:particle_appearance_billboard":{"size":[0.3,0.3],"facing_camera_mode":"rotate_xyz"},
                    "minecraft:particle_appearance_tinting":{"color":[1,0,1,1]}}}}
                    """),env);
            var p=mc.gameRenderer.mainCamera().position().add(mc.player.getLookAngle().scale(2));
            var matrix=new org.joml.Matrix4f().translation((float)p.x,(float)p.y,(float)p.z);
            var emitter=runtime.addEmitter(mc.level,matrix,net.minecraft.world.phys.Vec3.ZERO,def,"",mc.player);
            if(emitter==null)throw new AssertionError("Cannot create scope particle probe");
            // A detached world-space local effect; deliberately no animated locator to move it off-axis.
            runtime.bindAnimated(emitter,mc.player.getId(),Identifier.parse("tacz:m700"),"scope_probe_static",true,matrix,matrix);
        });
        try {
            ctx.waitTicks(4);
            var image=javax.imageio.ImageIO.read(ctx.takeScreenshot("tacz_amg10_particle_"+zoom).toFile());
            int magenta=0,r=image.getHeight()/16;
            for(int y=image.getHeight()/2-r;y<image.getHeight()/2+r;y++)for(int x=image.getWidth()/2-r;x<image.getWidth()/2+r;x++) {
                int c=image.getRGB(x,y),b=baseline.getRGB(x,y);
                int dr=(c>>>16&255)-(b>>>16&255),dg=(c>>>8&255)-(b>>>8&255),db=(c&255)-(b&255);
                // Additive magenta over blue sky is pale pink, not saturated magenta.
                // Measure the injected red/blue energy against the same ADS frame;
                // sky brightness alone must never satisfy the positive control.
                if(dr>30 && db>=0 && dr+db-2*dg>40)magenta++;
            }
            if(zoom<2?magenta>0:magenta<8)throw new AssertionError("Owned particle aperture policy zoom="+zoom+" magenta="+magenta);
            GunMod.LOGGER.info("PORT_SCOPE_PARTICLE: AMG10 zoom={} central magenta={}",zoom,magenta);
        } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        finally { ctx.runOnClient(mc->runtime.clear()); }
    }
}
