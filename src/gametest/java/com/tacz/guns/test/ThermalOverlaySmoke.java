package com.tacz.guns.test;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

final class ThermalOverlaySmoke {
    static void run(ClientGameTestContext ctx,TestSingleplayerContext world){
        var scope=Identifier.parse("port_test:thermal_overlay");
        boolean heat=ctx.computeOnClient(mc->RenderConfig.SCOPE_THERMAL_ENABLE.get());
        try{
            world.getServer().runOnServer(s->world.getConnection().getServerPlayer().setItemInHand(InteractionHand.MAIN_HAND,
                    GunItemBuilder.create().setId(Identifier.parse("tacz:m700")).putAttachment(AttachmentType.SCOPE,scope).setAmmoCount(3).build()));
            ctx.waitFor(mc->mc.player.getMainHandItem().getItem() instanceof IGun gun&&scope.equals(gun.getAttachmentId(mc.player.getMainHandItem(),AttachmentType.SCOPE)));
            ctx.getInput().holdKey(AimKey.AIM_KEY);
            ctx.waitFor(mc->IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(1)>.99f);
            int[] counts=new int[2];
            for(int i=0;i<2;i++){
                final boolean enabled=i==1;ctx.runOnClient(mc->RenderConfig.SCOPE_THERMAL_ENABLE.set(enabled));ctx.waitTicks(15);
                try{BufferedImage image=ImageIO.read(ctx.takeScreenshot("tacz_overlay_"+enabled).toFile());int w=image.getWidth(),h=image.getHeight();
                    for(int y=h/4;y<3*h/4;y++)for(int x=w/3;x<2*w/3;x++){int c=image.getRGB(x,y),r=(c>>16)&255,g=(c>>8)&255,b=c&255;if(r>100&&r>g*2&&r>b*2)counts[i]++;}
                }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
            }
            if(counts[0]<8||counts[1]<counts[0]*.85)throw new AssertionError("Thermal converted display color: "+counts[0]+" -> "+counts[1]);
            GunMod.LOGGER.info("PORT_THERMAL_OVERLAY: red glyph pixels {} optical / {} thermal",counts[0],counts[1]);
        }finally{ctx.getInput().releaseKey(AimKey.AIM_KEY);ctx.runOnClient(mc->RenderConfig.SCOPE_THERMAL_ENABLE.set(heat));}
    }
}
