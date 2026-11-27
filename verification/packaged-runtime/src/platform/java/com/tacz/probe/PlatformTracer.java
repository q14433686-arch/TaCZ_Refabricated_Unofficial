package com.tacz.probe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.entity.EntityKineticBullet;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Consume actual native tracer submissions to vertices, including the frozen model snapshot. */
final class PlatformTracer {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var camera=mc.gameRenderer.getMainCamera();var rotation=new Quaternionf(camera.rotation());
            var muzzle=new Vector3f(GunItemRendererWrapper.muzzleRenderOffset);
            boolean enabled=RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.get();
            var bullet=new EntityKineticBullet(EntityKineticBullet.TYPE,mc.level);
            bullet.setOwner(mc.player);bullet.setPos(mc.player.getEyePosition().add(0,0,4));bullet.setDeltaMovement(0,0,1);bullet.tickCount=10;
            var renderer=(EntityBulletRenderer)mc.getEntityRenderDispatcher().getRenderer(bullet);
            try {
                RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.set(true);GunItemRendererWrapper.muzzleRenderOffset.set(.2f,.3f,.4f);
                var first=vertices(renderer,bullet);
                if(first.isEmpty())throw new AssertionError("Tracer did not submit actual geometry");
                var frozen=new Quaternionf(bullet.getFirstPersonCameraRotation());
                camera.rotation().rotateXYZ(.3f,.8f,.2f);GunItemRendererWrapper.muzzleRenderOffset.set(9,8,7);
                var moved=vertices(renderer,bullet);
                if(!same(first,moved) || !frozen.equals(bullet.getFirstPersonCameraRotation()))
                    throw new AssertionError("Existing shot followed live camera roll or next-shot muzzle locator");
                bullet.setCameraXRot(24);bullet.setCameraYRot(67);
                if(bullet.getFirstPersonCameraRotation()!=null)throw new AssertionError("Legacy Euler setter retained stale quaternion");
                if(same(first,vertices(renderer,bullet)))throw new AssertionError("Legacy Euler compatibility path had no effect");
            } finally {
                camera.rotation().set(rotation);GunItemRendererWrapper.muzzleRenderOffset.set(muzzle);
                RenderConfig.FIRST_PERSON_BULLET_TRACER_ENABLE.set(enabled);bullet.discard();
            }
        });
        System.out.println("PORT_PLATFORM_TRACER: actual submitted tracer vertices stayed fixed across live camera yaw/pitch/roll and muzzle changes; legacy Euler invalidation remained effective");
    }
    private static List<Vector3f> vertices(EntityBulletRenderer renderer,EntityKineticBullet bullet) {
        var result=new ArrayList<Vector3f>();
        var collector=new SubmitNodeStorage(){
            @Override public void submitCustomGeometry(PoseStack pose,RenderType type,CustomGeometryRenderer draw){draw.render(pose.last(),new Vertices(result));}
        };
        renderer.renderTracerAmmo(bullet,new float[]{1,1,1},1,new PoseStack(),collector,15728880);
        return result;
    }
    private static boolean same(List<Vector3f> a,List<Vector3f> b){if(a.size()!=b.size())return false;for(int i=0;i<a.size();i++)if(!a.get(i).equals(b.get(i),.000001f))return false;return true;}
    private record Vertices(List<Vector3f> points) implements VertexConsumer {
        public VertexConsumer addVertex(float x,float y,float z){points.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setColor(int color){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
        public VertexConsumer setLineWidth(float width){return this;}
    }
    private PlatformTracer(){}
}
