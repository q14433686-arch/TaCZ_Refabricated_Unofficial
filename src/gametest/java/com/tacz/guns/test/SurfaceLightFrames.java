package com.tacz.guns.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Read the real framebuffer at render END, after the first main-world collection following
 * the event. An OFF shot/detonation follows exactly the same path; no pulse clock is frozen. */
final class SurfaceLightFrames {
    enum Trigger { STILL, SHOT, EXPLOSION }
    private static Trigger waiting;
    private static String name;
    private static boolean triggered, collected;
    private static View view;
    private static volatile Path saved;

    static void arm(String label, Trigger event) {
        ReceiverInputs.arm(label);
        name="tacz_surface_"+label+".png"; waiting=event; triggered=event==Trigger.STILL; collected=false; saved=null;
    }
    static void trigger(Trigger event) { if (waiting==event) triggered=true; }
    static boolean triggered() { return triggered && !collected; }
    static void afterCollection() { if (triggered()) { ReceiverInputs.collected(); collected=true; } }
    static void renderEnd(Minecraft mc) {
        if (!collected) return;
        collected=triggered=false; waiting=null;
        var state=mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
        view=new View(state.pos,new Matrix4f(state.viewRotationMatrix),Math.abs(state.projectionMatrix.m00()),Math.abs(state.projectionMatrix.m11()));
        var path=mc.gameDirectory.toPath().resolve("screenshots").resolve(name);
        Screenshot.grab(mc.gameDirectory,name,mc.gameRenderer.mainRenderTarget(),1, message -> saved=path);
    }
    static void clear() { waiting=null; triggered=collected=false; saved=null; }
    static Frame read(ClientGameTestContext ctx) {
        ctx.waitFor(mc->saved!=null,100);
        return new Frame(SnowstormMaterialSmoke.read(saved),view);
    }
    static Frame still(ClientGameTestContext ctx,String label) {
        ctx.runOnClient(mc->arm(label,Trigger.STILL)); return read(ctx);
    }
    private record View(Vec3 camera, Matrix4f rotation, float fx, float fy) {}
    record Region(Vec3 center, boolean horizontal) {
        static Region wall(Vec3 p) { return new Region(p,false); }
        static Region floor(Vec3 p) { return new Region(p,true); }
    }
    record Frame(BufferedImage image, View view) {
        /** Compare the same piece of terrain, not the held gun, crosshair, explosion sprite
         * or a fixed screen rectangle that moves off the receiver when the camera recoils. */
        int rgb(Vec3 point) {
            int w=image.getWidth(), h=image.getHeight();
            var p=view.rotation.transformDirection(point.subtract(view.camera).toVector3f());
            if (!(p.z < -.1)) throw new AssertionError("Receiver behind observer: "+point);
            int x=(int)(w*.5f*(1-view.fx*p.x/p.z)), y=(int)(h*.5f*(1+view.fy*p.y/p.z));
            if (x<0 || y<0 || x>=w || y>=h) throw new AssertionError("Receiver outside frame: "+point+" -> "+x+","+y+" in "+w+"x"+h);
            return image.getRGB(x,y);
        }
        int meanRed(Region region, double extent) { return mean(region, extent, 16, 4); }
        int mean(Region region, double extent, int shift, int halfGrid) {
            int sum=0;
            for (int j=-halfGrid;j<=halfGrid;j++) for (int i=-halfGrid;i<=halfGrid;i++) {
                // Sample on the plane itself. A square screen ROI at grazing incidence
                // could include the far wall or the sky rather than just the floor.
                double v=j*extent/halfGrid;
                var point=region.center.add(i*extent/halfGrid,region.horizontal ? 0 : v,region.horizontal ? v : 0);
                sum+=rgb(point)>>shift&255;
            }
            int side=2*halfGrid+1; return sum/(side*side);
        }
        int[] deltas(Frame reference, Region[] points, double extent) {
            int[] d=new int[points.length];
            for (int i=0;i<d.length;i++) d[i]=meanRed(points[i],extent)-reference.meanRed(points[i],extent);
            return d;
        }
    }
    private SurfaceLightFrames() {}
}
