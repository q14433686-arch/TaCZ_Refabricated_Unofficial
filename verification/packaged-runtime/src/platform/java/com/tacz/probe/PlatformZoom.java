package com.tacz.probe;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.client.render.scope.ScopeProjection;
import com.tacz.guns.compat.zoomify.ZoomifyCompat;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

final class PlatformZoom {
    static void verify(ClientGameTestContext ctx) {
        boolean installed=FabricLoader.getInstance().isModLoaded("zoomify");
        if(!installed) {
            ctx.runOnClient(mc -> { if(ZoomifyCompat.getFov(70,.5f)!=70) throw new AssertionError("Absent Zoomify changed FOV"); });
            System.out.println("PORT_PLATFORM_ZOOM: absent optional classes, FOV identity passed");return;
        }
        try {
            var type=Class.forName("dev.isxander.zoomify.Zoomify");
            var field=type.getDeclaredField("zoomKey");field.setAccessible(true);
            var key=(KeyMapping)field.get(null);
            var original=KeyBindingHelper.getBoundKeyOf(key);
            var divisor=type.getMethod("getZoomDivisor",float.class);
            ctx.runOnClient(mc -> { key.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F7));KeyMapping.resetMapping(); });
            float before=ctx.computeOnClient(mc -> ScopeProjection.levelProjectionM11());
            try {
                ctx.getInput().holdKey(key);ctx.waitTicks(25);
                ctx.runOnClient(mc -> {
                    try {
                        float d=((Number)divisor.invoke(null,.5f)).floatValue();
                        if(!(d>1.5f) || Math.abs(ZoomifyCompat.getFov(70,.5f)-70/d)>.0001)
                            throw new AssertionError("Installed Zoomify FOV integration failed: divisor="+d);
                        if(!(ScopeProjection.levelProjectionM11()>before*1.2f)) throw new AssertionError("Real main projection never zoomed");
                    } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
                });
            } finally {
                ctx.getInput().releaseKey(key);ctx.waitTicks(25);
                ctx.runOnClient(mc -> { key.setKey(original);KeyMapping.resetMapping(); });
            }
            ctx.runOnClient(mc -> {
                if(Math.abs(ZoomifyCompat.getFov(70,.5f)-70)>.001 || Math.abs(ScopeProjection.levelProjectionM11()/before-1)>.03)
                    throw new AssertionError("Zoom release leaked FOV");
            });
            System.out.println("PORT_PLATFORM_ZOOM: installed Zoomify physical key, public divisor contract, actual main projection and release passed");
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private PlatformZoom() {}
}
