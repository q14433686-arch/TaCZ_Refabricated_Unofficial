package com.tacz.probe;

import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.input.*;
import com.tacz.guns.config.client.KeyConfig;
import com.tacz.guns.util.InputExtraCheck;
import com.tacz.probe.mixin.NativeInput;
import com.tacz.probe.mixin.NativeMouse;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.Map;

/** Actual 1.21.11 native keyboard and mouse callbacks, not calls to individual key handlers. */
public final class PlatformSmoke extends PlayerProbe {
    public static int inspects,reloads,unloads;
    @Override void exercise(ClientGameTestContext ctx,TestSingleplayerContext world) {
        Map<KeyMapping,InputConstants.Key> saved=new LinkedHashMap<>();
        var auto=ctx.computeOnClient(mc -> KeyConfig.AUTO_RELOAD.get());
        ctx.runOnClient(mc -> {
            KeyConfig.AUTO_RELOAD.set(false);
            for(var key:new KeyMapping[]{InspectKey.INSPECT_KEY,ReloadKey.RELOAD_KEY,ReloadKey.UNLOAD_KEY,RefitKey.REFIT_KEY})
                saved.put(key,KeyBindingHelper.getBoundKeyOf(key));
        });
        try {
            ctx.waitFor(mc -> InputExtraCheck.isInGame());
            ctx.runOnClient(mc -> {
                bind(InspectKey.INSPECT_KEY,InputConstants.Type.SCANCODE.getOrCreate(203));
                int n=inspects;key(mc,-1,203,GLFW.GLFW_PRESS,0);
                if(inspects!=n+1) throw new AssertionError("Scancode-only physical press lost or duplicated: "+n+"/"+inspects);
                key(mc,-1,203,GLFW.GLFW_REPEAT,0);key(mc,-1,203,GLFW.GLFW_RELEASE,0);
                if(inspects!=n+1) throw new AssertionError("Repeat/release dispatched an inspect");
                bind(InspectKey.INSPECT_KEY,InputConstants.Type.MOUSE.getOrCreate(4));
                mouse(mc,4,GLFW.GLFW_PRESS,0);mouse(mc,4,GLFW.GLFW_RELEASE,0);
                if(inspects!=n+2) throw new AssertionError("Inspect mouse rebind not routed once");
                mc.setScreen(new PauseScreen(false));
                mouse(mc,4,GLFW.GLFW_PRESS,0);mouse(mc,4,GLFW.GLFW_RELEASE,0);
                if(inspects!=n+2) throw new AssertionError("Screen input reached gun action");
                mc.setScreen(null);
            });
            ctx.waitFor(mc -> InputExtraCheck.isInGame());
            ctx.runOnClient(mc -> {
                bind(RefitKey.REFIT_KEY,InputConstants.Type.MOUSE.getOrCreate(5));
                mouse(mc,5,GLFW.GLFW_PRESS,0);mouse(mc,5,GLFW.GLFW_RELEASE,0);
                if(!(mc.screen instanceof GunRefitScreen)) throw new AssertionError("Mouse could not open refit");
            });
            ctx.waitTick();
            ctx.runOnClient(mc -> {
                ((NativeMouse)mc.mouseHandler).platform$mouse(mc.getWindow().handle()+1,new MouseButtonInfo(5,0),GLFW.GLFW_PRESS);
                if(!(mc.screen instanceof GunRefitScreen)) throw new AssertionError("Foreign-window mouse callback closed refit");
                mouse(mc,5,GLFW.GLFW_PRESS,0);mouse(mc,5,GLFW.GLFW_RELEASE,0);
                if(mc.screen!=null) throw new AssertionError("Refit mouse close failed or reopened through post callback");
            });
            ctx.waitFor(mc -> InputExtraCheck.isInGame());
            ctx.runOnClient(mc -> {
                bind(ReloadKey.RELOAD_KEY,InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
                bind(ReloadKey.UNLOAD_KEY,InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
                int r=reloads,u=unloads;
                key(mc,GLFW.GLFW_KEY_R,19,GLFW.GLFW_PRESS,0);key(mc,GLFW.GLFW_KEY_R,19,GLFW.GLFW_RELEASE,0);
                if(reloads!=r || unloads!=u+1) throw new AssertionError("Unload conflict fell through into reload");
                bind(ReloadKey.UNLOAD_KEY,InputConstants.UNKNOWN);
                key(mc,GLFW.GLFW_KEY_R,19,GLFW.GLFW_PRESS,GLFW.GLFW_MOD_ALT);
                key(mc,GLFW.GLFW_KEY_R,19,GLFW.GLFW_RELEASE,0);
                if(reloads!=r || unloads!=u+2) throw new AssertionError("ALT reload did not exclusively unload");
                bind(ReloadKey.RELOAD_KEY,InputConstants.Type.MOUSE.getOrCreate(4));
                mouse(mc,4,GLFW.GLFW_PRESS,0);mouse(mc,4,GLFW.GLFW_RELEASE,0);
                if(reloads!=r+1) throw new AssertionError("Mouse reload lost/duplicated");
            });
            System.out.println("PORT_PLATFORM_INPUT: native key/scancode/mouse press, repeat/release, screen veto, refit open/close, unload collision and ALT priority passed");
            PlatformSkins.verify(ctx);
            PlatformAudio.verify(ctx);
            PlatformGui.verify(ctx);
            PlatformZoom.verify(ctx);
            PlatformRecoil.verify(ctx,world);
            PlatformAds.verify(ctx);
            PlatformModifiers.verify(ctx);
        } finally {
            ctx.runOnClient(mc -> { mc.setScreen(null);saved.forEach(PlatformSmoke::bind);KeyConfig.AUTO_RELOAD.set(auto); });
        }
    }
    private static void bind(KeyMapping mapping,InputConstants.Key key) { mapping.setKey(key);KeyMapping.resetMapping(); }
    private static void key(Minecraft mc,int key,int scan,int action,int mods) {
        ((NativeInput)mc.keyboardHandler).platform$key(mc.getWindow().handle(),action,new KeyEvent(key,scan,mods));
    }
    private static void mouse(Minecraft mc,int button,int action,int mods) {
        ((NativeMouse)mc.mouseHandler).platform$mouse(mc.getWindow().handle(),new MouseButtonInfo(button,mods),action);
    }
}
