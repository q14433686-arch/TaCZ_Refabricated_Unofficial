package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Native special-renderer output under non-identity authored local transforms. */
public final class SpriteTransformProbe {
    public static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc->{
            var pose=new PoseStack();pose.translate(3,4,5);pose.scale(.5f,.75f,1);
            var original=new Matrix4f(pose.last().pose());
            var local=new Matrix4f().translate(.2f,.3f,.4f).rotateY(.4f).scale(2,3,4);
            var frozen=new Matrix4f(local);
            var argument=new DroppedGunSprite.Argument(Identifier.parse("minecraft:textures/block/gold_block.png"),local);
            local.identity(); // a late author-side mutation cannot change already-extracted appearance
            var points=new ArrayList<Vector3f>();
            var collector=new SubmitNodeStorage(){@Override public void submitCustomGeometry(PoseStack p,RenderType type,CustomGeometryRenderer draw){draw.render(p.last(),new Vertices(points));}};
            DroppedGunSprite.INSTANCE.submit(argument,ItemDisplayContext.GROUND,pose,collector,15728880,0,false,0);
            if(points.size()!=4 || !pose.last().pose().equals(original))throw new AssertionError("Sprite altered caller pose or quad cardinality");
            var combined=new Matrix4f(original).mul(frozen);
            var center=combined.transformPosition(.5f,.5f,.5f,new Vector3f());var scale=combined.getScale(new Vector3f());
            var inverse=new org.joml.Quaternionf(Minecraft.getInstance().gameRenderer.getMainCamera().rotation()).conjugate();
            float[][] signs={{-1,-1},{1,-1},{1,1},{-1,1}};
            for(int i=0;i<4;i++){
                var v=new Vector3f(points.get(i)).sub(center).rotate(inverse);
                var expected=new Vector3f(signs[i][0]*.375f*scale.x,signs[i][1]*.375f*scale.y,0);
                if(!v.equals(expected,.00001f))throw new AssertionError("Dropped sprite lost local transform/billboard corner: "+v+" != "+expected);
            }
        });
        System.out.println("PORT_PLATFORM_SPRITE_TRANSFORM: actual special renderer submitted four camera-facing corners at authored translated/scaled center; frozen transform and caller pose isolation passed");
    }
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
    private SpriteTransformProbe(){}
}
