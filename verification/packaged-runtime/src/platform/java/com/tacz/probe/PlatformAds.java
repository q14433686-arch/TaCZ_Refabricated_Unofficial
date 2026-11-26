package com.tacz.probe;

import cn.sh1rocu.simplebedrockmodel.api.event.ViewportEvent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.pojo.display.gun.AdsFov;
import com.tacz.guns.client.resource.pojo.display.gun.AdsPath;
import com.tacz.guns.client.resource.pojo.display.gun.GunDisplay;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Controlled authored channels through the registered native viewport callbacks and actual
 * first-person positioning consumer, not just JSON getters or curve.evaluate(). */
final class PlatformAds {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var stack=KeepingItemRenderer.getRenderer().getCurrentItem();
            var instance=TimelessAPI.getGunDisplay(stack).orElseThrow();
            var display=(GunDisplay)read(instance,"display");
            var oldFov=read(display,"adsFov");var oldPath=read(display,"adsPath");
            var oldModel=read(instance,"modelFov");var oldZoom=read(instance,"zoomModelFov");
            boolean oldPip=RenderConfig.SCOPE_PIP_ENABLE.get();
            var data=IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder();
            float oldProgress=data.clientAimingProgress,oldPrevious=LocalPlayerDataHolder.oldAimingProgress;
            try {
                RenderConfig.SCOPE_PIP_ENABLE.set(false);
                write(instance,"modelFov",72f);write(instance,"zoomModelFov",46f);
                write(display,"adsFov",ClientAssetsManager.GSON.fromJson("""
                    {"world":{"start":0.25,"end":0.75,"ease":"ease_in_quad","smooth":false},
                     "model":{"ease":"ease_out_quad","smooth":false}}
                    """,AdsFov.class));
                float zoom=((IGun)stack.getItem()).getAimingZoom(stack);
                for(float p:new float[]{0,.25f,.5f,.75f,1}) {
                    data.clientAimingProgress=LocalPlayerDataHolder.oldAimingProgress=p;
                    var world=new ViewportEvent.ComputeFov(mc.gameRenderer.getMainCamera(),.5,true,80);
                    var model=new ViewportEvent.ComputeFov(mc.gameRenderer.getMainCamera(),.5,false,70);
                    ViewportEvent.FOV.invoker().onComputeFov(world);ViewportEvent.FOV.invoker().onComputeFov(model);
                    float t=Math.max(0,Math.min(1,(p-.25f)/.5f));
                    double expectedWorld=Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(80)/2)/(1+(zoom-1)*t*t)));
                    near(world.getFOV(),expectedWorld,"world authored channel p="+p);
                    near(model.getFOV(),72+(46-72)*(1-(1-p)*(1-p)),"model authored channel p="+p);
                }
                var model=instance.getGunModel();
                if(model==null) throw new AssertionError("No stock rig for ADS consumer");
                var idle=read(model,"idleSightPath");var iron=read(model,"ironSightPath");
                try {
                    var a=new BedrockPart("probe_idle");var b=new BedrockPart("probe_aim");
                    a.setPos(0,0,0);b.setPos(16,-32,48);
                    write(model,"idleSightPath",List.of(a));write(model,"ironSightPath",List.of(b));
                    write(display,"adsPath",new AdsPath());
                    Matrix4f linear=position(model,stack,.5f);
                    write(display,"adsPath",ClientAssetsManager.GSON.fromJson("""
                        {"x":{"ease":"ease_in_quad"},"y":{"ease":"ease_out_quad"},
                         "z":{"start":0.5,"end":1},"rotation":{"ease":"linear"}}
                        """,AdsPath.class));
                    Matrix4f curved=position(model,stack,.5f);
                    // Inverse Bedrock node translation: (-x,-y,-z)/16. Rotation unchanged.
                    near(curved.m30()-linear.m30(),.25,"ADS path X");
                    near(curved.m31()-linear.m31(),.5,"ADS path Y");
                    near(curved.m32()-linear.m32(),1.5,"ADS path Z");
                    write(display,"adsPath",new AdsPath());
                    Matrix4f restored=position(model,stack,.5f);
                    if(!linear.equals(restored,.0001f)) throw new AssertionError("ADS consumer retained old authored path");
                } finally {write(model,"idleSightPath",idle);write(model,"ironSightPath",iron);}
            } finally {
                write(display,"adsFov",oldFov);write(display,"adsPath",oldPath);write(instance,"modelFov",oldModel);write(instance,"zoomModelFov",oldZoom);
                data.clientAimingProgress=oldProgress;LocalPlayerDataHolder.oldAimingProgress=oldPrevious;RenderConfig.SCOPE_PIP_ENABLE.set(oldPip);
            }
        });
        System.out.println("PORT_PLATFORM_ADS: registered world/model authored FOV channels at five progress points and actual first-person independent XYZ positioning/restoration passed");
    }
    private static Matrix4f position(BedrockGunModel model,ItemStack stack,float p) {
        try {
            Method m=FirstPersonRenderGunEvent.class.getDeclaredMethod("applyFirstPersonPositioningTransform",PoseStack.class,BedrockGunModel.class,ItemStack.class,float.class,float.class);
            m.setAccessible(true);var pose=new PoseStack();m.invoke(null,pose,model,stack,p,0f);return new Matrix4f(pose.last().pose());
        } catch(ReflectiveOperationException e) {throw new AssertionError(e);}
    }
    static Object read(Object owner,String name) {try{return field(owner.getClass(),name).get(owner);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static void write(Object owner,String name,Object value) {try{field(owner.getClass(),name).set(owner,value);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    private static Field field(Class<?> type,String name) throws NoSuchFieldException {
        for(Class<?> c=type;c!=null;c=c.getSuperclass()) try {var f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(type+"."+name);
    }
    private static void near(double actual,double expected,String message) {if(Math.abs(actual-expected)>.0002)throw new AssertionError(message+": "+actual+" != "+expected);}
    private PlatformAds() {}
}
