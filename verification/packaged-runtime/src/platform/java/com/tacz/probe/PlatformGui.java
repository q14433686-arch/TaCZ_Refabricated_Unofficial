package com.tacz.probe;

import com.tacz.probe.mixin.PlatformCooldown;
import me.xjqsh.lrtactical.api.item.ICustomItem;
import me.xjqsh.lrtactical.init.ModCapabilities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Draw through the actual private GuiGraphics cooldown method and LRT tail injection.
 * Neutral calibration tiles isolate the mask from item albedo, animation and world changes. */
public final class PlatformGui implements ClientModInitializer {
    private static boolean enabled;
    private static ItemStack first,second;
    @Override public void onInitializeClient() {
        PlatformSnowstormSpace.register();
        HudElementRegistry.addLast(Identifier.parse("tacz_packaged_probe:cooldown"),(gui,delta) -> {
            if(!enabled) return;
            gui.fill(4,4,20,20,0xff000000);gui.fill(28,4,44,20,0xff000000);
            ((PlatformCooldown)gui).platform$cooldown(first,4,4);
            ((PlatformCooldown)gui).platform$cooldown(second,28,4);
        });
    }
    static void verify(ClientGameTestContext ctx) {
        var id=ctx.computeOnClient(mc -> {
            first=new ItemStack(PlatformItems.first);second=new ItemStack(PlatformItems.second);enabled=true;
            return ((ICustomItem)first.getItem()).getCoolDownId(first).orElseThrow();
        });
        try {
            ctx.waitTicks(2);
            var before=read(ctx,"before");
            ctx.runOnClient(mc -> ModCapabilities.coolDowns(mc.player).addCooldown(id,80));
            ctx.waitTicks(40);
            var half=read(ctx,"half");
            int scale=ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
            int changed=changed(before,half,4,scale),other=changed(before,half,28,scale);
            int area=256*scale*scale;
            if(changed<area/4 || changed>area*3/4 || other!=0)
                throw new AssertionError("Native cooldown half mask="+changed+"/"+area+" unrelated category="+other);
            ctx.waitTicks(50);
            var expired=read(ctx,"expired");
            if(changed(before,expired,4,scale)!=0 || changed(before,expired,28,scale)!=0)
                throw new AssertionError("Expired custom cooldown left GUI residue");
            System.out.println("PORT_PLATFORM_GUI: native LRT cooldown mask "+changed+"/"+area+" half-progress pixels; independent category and expired pixels=0");
        } finally {
            ctx.runOnClient(mc -> {enabled=false;ModCapabilities.coolDowns(mc.player).removeCooldown(id);first=second=null;});
        }
    }
    private static BufferedImage read(ClientGameTestContext ctx,String name) {
        try {return ImageIO.read(ctx.takeScreenshot("tacz_platform_cooldown_"+name).toFile());}
        catch(IOException e) {throw new java.io.UncheckedIOException(e);}
    }
    private static int changed(BufferedImage a,BufferedImage b,int x,int scale) {
        int n=0;
        for(int y=4*scale;y<20*scale;y++) for(int i=x*scale;i<(x+16)*scale;i++)
            if(Math.abs((a.getRGB(i,y)&255)-(b.getRGB(i,y)&255))>10) n++;
        return n;
    }
}
