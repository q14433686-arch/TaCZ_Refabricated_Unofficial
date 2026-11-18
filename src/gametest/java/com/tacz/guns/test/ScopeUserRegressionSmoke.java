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
}
