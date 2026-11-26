package com.tacz.probe;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.client.render.scope.ScopeProjection;
import com.tacz.guns.compat.iris.IrisCompat;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.concurrent.CompletableFuture;

/** Observe the actual native submit inputs and first completed main-world frame after a shot.
 * Neither pulse lifetime nor projection/render output is replaced by this fixture. */
public final class LightFrames {
    private static boolean registered;
    private static Request pending;
    private static View view;
    private static Vec3 cow,stand,drop;
    private static BlockPos statue,board,cabinet;
    public static int shots;
    private static boolean faultProbe;
    static int exceptionRestores;
    private static final ArrayDeque<IdentityHashMap<Object,Integer>> scopes=new ArrayDeque<>();
    private record View(Vec3 camera,Matrix4f rotation,float fx,float fy) {}
    private static final class Request {
        final String name;
        final CompletableFuture<Frame> result=new CompletableFuture<>();
        final int[] light={-1,-1,-1,-1,-1,-1};
        boolean triggered,submitted;
        Request(String name,boolean triggered) { this.name=name; this.triggered=triggered; }
    }
    static void setup(Vec3 a,Vec3 b,Vec3 c,BlockPos s,BlockPos normal,BlockPos fullbright) {
        cow=a;stand=b;drop=c;statue=s;board=normal;cabinet=fullbright;exceptionRestores=0;
        if(registered) return; registered=true;
        GunFirePostEvent.EVENT.register(e -> {
            if(e.getLogicalSide().isClient() && e.getShooter()==Minecraft.getInstance().player) {
                shots++; if(pending!=null) pending.triggered=true;
            }
        });
        RenderTickEvent.EVENT.register(e -> { if(e.phase==RenderTickEvent.Phase.END) renderEnd(); });
    }
    static void clear() { pending=null;scopes.clear();cow=stand=drop=null;statue=board=cabinet=null; }
    private static boolean observing() { return pending!=null && pending.triggered && !ScopePipRerender.isInsideScopeLevelRender() && !IrisCompat.isRenderShadow(); }
    public static void worldView(Matrix4f rotation) {
        var mc=Minecraft.getInstance();float fy=ScopeProjection.levelProjectionM11();
        view=new View(mc.gameRenderer.getMainCamera().position(),new Matrix4f(rotation),fy*mc.getWindow().getHeight()/mc.getWindow().getWidth(),fy);
    }
    public static void beginScope(LevelRenderState state,boolean entities) {
        if(faultProbe) return;
        var saved=new IdentityHashMap<Object,Integer>();
        if(observing()) {
            if(entities) for(var e:state.entityRenderStates) saved.put(e,e.lightCoords);
            else for(var b:state.blockEntityRenderStates) saved.put(b,b.lightCoords);
        }
        scopes.push(saved);
    }
    public static void endScope() {
        if(faultProbe) return;
        var saved=scopes.pop();
        saved.forEach((state,old) -> {
            int now=state instanceof EntityRenderState e ? e.lightCoords : ((BlockEntityRenderState)state).lightCoords;
            if(now!=old) throw new AssertionError("Receiver light leaked beyond native submission: "+old+" -> "+now);
        });
    }
    public static void submitted(LevelRenderState state,boolean entities) {
        if(faultProbe || !observing()) return;
        if(entities && pending.name.equals("on") && exceptionRestores==0) exceptionRestore(state);
        pending.submitted=true;
        if(entities) for(var e:state.entityRenderStates) {
            int i=e.entityType==EntityType.COW ? 0 : e.entityType==EntityType.ARMOR_STAND ? 1 : e.entityType==EntityType.ITEM ? 2 : -1;
            Vec3 p=i==0?cow:i==1?stand:drop;
            if(i>=0 && p!=null && p.distanceToSqr(e.x,e.y,e.z)<.12) pending.light[i]=e.lightCoords;
        }
        else for(var b:state.blockEntityRenderStates) if(statue!=null && statue.equals(b.blockPos)) pending.light[3]=b.lightCoords;
    }
    /** A test-only nested original throws, then the real outer draw proceeds with
     * exactly its original lit state. Exercise Java 21 finally restoration, not a fake renderer. */
    private static void exceptionRestore(LevelRenderState state) {
        var parent=com.tacz.guns.client.render.light.WorldFlashFrame.current();
        if(parent==null) throw new AssertionError("No live receiver context on the ON submission");
        var body=state.entityRenderStates.stream().filter(e -> e.entityType==EntityType.COW).findFirst().orElseThrow();
        int lit=body.lightCoords,ambient=net.minecraft.client.renderer.LightTexture.pack(0,net.minecraft.client.renderer.LightTexture.sky(lit));
        boolean raised=false;
        faultProbe=true;body.lightCoords=ambient;
        try {
            try {
                com.tacz.guns.client.render.light.WorldFlashFrame.submitWorld(state,true,() -> {
                    if(body.lightCoords==ambient) throw new AssertionError("Nested receiver never received source light");
                    throw new ExpectedSubmissionFailure();
                });
            } catch(ExpectedSubmissionFailure expected) { raised=true; }
            if(!raised || body.lightCoords!=ambient || com.tacz.guns.client.render.light.WorldFlashFrame.current()!=parent)
                throw new AssertionError("Nested exception leaked receiver input or Java 21 light context");
            exceptionRestores++;
        } finally { body.lightCoords=lit;faultProbe=false; }
    }
    private static final class ExpectedSubmissionFailure extends RuntimeException {}
    public static void rack(BlockPos pos,int light) {
        if(!observing()) return;
        if(pos.equals(board)) pending.light[4]=light;
        if(pos.equals(cabinet)) pending.light[5]=light;
    }
    private static void renderEnd() {
        var request=pending;
        if(request==null || !request.triggered || !request.submitted) return;
        pending=null;
        var camera=view;
        int[] light=request.light.clone();
        var mc=Minecraft.getInstance();
        OpticsReadback.readColor(mc.getMainRenderTarget()).whenComplete((image,error) -> {
            if(error!=null) { request.result.completeExceptionally(error);return; }
            try {
                var file=mc.gameDirectory.toPath().resolve("screenshots/tacz_receiver_"+request.name+".png");
                ImageIO.write(image,"png",file.toFile());
                request.result.complete(new Frame(image,camera,light));
            } catch(Throwable e) { request.result.completeExceptionally(e); }
        });
    }
    static CompletableFuture<Frame> arm(String name,boolean still) { pending=new Request(name,still);shots=0;return pending.result; }
    static Frame read(ClientGameTestContext ctx,CompletableFuture<Frame> result) {
        ctx.waitFor(mc -> result.isDone(),120);return result.join();
    }
    static Frame still(ClientGameTestContext ctx,String name) { var f=ctx.computeOnClient(mc -> arm(name,true));return read(ctx,f); }
    record Frame(BufferedImage image,View view,int[] light) {
        int rgb(Vec3 point) {
            var p=view.rotation.transformPosition(point.subtract(view.camera).toVector3f());
            if(p.z>=-.1) throw new AssertionError("Receiver behind camera: "+point);
            int x=(int)(image.getWidth()*.5f*(1-view.fx*p.x/p.z)),y=(int)(image.getHeight()*.5f*(1+view.fy*p.y/p.z));
            if(x<0 || y<0 || x>=image.getWidth() || y>=image.getHeight()) throw new AssertionError("Receiver outside frame: "+point+" -> "+x+","+y);
            return image.getRGB(x,y);
        }
        int mean(Vec3 center,double extent,int shift,boolean horizontal) {
            int sum=0,n=0;
            for(int j=-10;j<=10;j++) for(int i=-10;i<=10;i++) {
                double v=j*extent/10;
                sum+=rgb(center.add(i*extent/10,horizontal?0:v,horizontal?v:0))>>shift&255;n++;
            }
            return sum/n;
        }
        @Override public String toString() { return Arrays.toString(light); }
    }
    private LightFrames() {}
}
