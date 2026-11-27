package com.tacz.guns.client.particle.bedrock.particle.runtime;

import com.tacz.guns.client.particle.bedrock.particle.data.component.*;
import com.tacz.guns.client.particle.bedrock.particle.data.component.motion.ParticleMotionCollision;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.*;
import java.lang.Math;
import java.util.ArrayList;

/** Swept voxel-shape collision in world space, including local-space emitter transforms. */
final class ParticleWorldPhysics {
    static void move(ParticleEmitterInstance emitter,ParticleInstance p,float oldX,float oldY,float oldZ,float dt){
        var ctx=emitter.getEventContext();if(ctx==null||ctx.level()==null||p.fpDetached)return;
        var level=ctx.level();var definition=emitter.getDefinition();
        Matrix4f transform=p.worldSpace?new Matrix4f():new Matrix4f(emitter.getWorldTransform());
        var start=transform.transformPosition(new Vector3f(oldX,oldY,oldZ));
        var end=transform.transformPosition(new Vector3f(p.x,p.y,p.z));
        var collision=definition.findComponent(ParticleMotionCollision.class);
        if(collision!=null&&(collision.enabled()==null||collision.enabled().evaluate(emitter.getMolang().getContext())!=0)){
            double dx=end.x-start.x,dy=end.y-start.y,dz=end.z-start.z;
            double radius=Math.clamp(collision.collisionRadius(),.001f,4f);
            var box=new AABB(start.x-radius,start.y-radius,start.z-radius,start.x+radius,start.y+radius,start.z+radius);
            var shapes=new ArrayList<net.minecraft.world.phys.shapes.VoxelShape>();
            // Bound a malformed trajectory before requesting a potentially enormous chunk region.
            if(dx*dx+dy*dy+dz*dz>4096){p.alive=false;return;}
            for(var shape:level.getBlockCollisions(null,box.expandTowards(dx,dy,dz)))shapes.add(shape);
            double y=Shapes.collide(Direction.Axis.Y,box,shapes,dy);box=box.move(0,y,0);
            double x=Shapes.collide(Direction.Axis.X,box,shapes,dx);box=box.move(x,0,0);
            double z=Shapes.collide(Direction.Axis.Z,box,shapes,dz);
            if(x!=dx||y!=dy||z!=dz){
                var velocity=transform.transformDirection(new Vector3f(p.vx,p.vy,p.vz));
                float speed=velocity.length();float restitution=Math.clamp(collision.coefficientOfRestitution(),0,1);
                if(x!=dx)velocity.x=-velocity.x*restitution;
                if(y!=dy)velocity.y=-velocity.y*restitution;
                if(z!=dz)velocity.z=-velocity.z*restitution;
                float len=velocity.length();if(len>0)velocity.mul(Math.max(0,len-collision.collisionDrag()*dt)/len);
                end.set((float)(start.x+x),(float)(start.y+y),(float)(start.z+z));
                new Matrix4f(transform).invert().transformDirection(velocity);
                p.vx=velocity.x;p.vy=velocity.y;p.vz=velocity.z;
                emitter.fireCollisionEvents(p,speed);if(collision.expireOnContact())p.alive=false;
                var local=new Matrix4f(transform).invert().transformPosition(new Vector3f(end));p.x=local.x;p.y=local.y;p.z=local.z;
            }
        }
        var id=BuiltInRegistries.BLOCK.getKey(level.getBlockState(BlockPos.containing(end.x,end.y,end.z)).getBlock()).toString();
        var inside=definition.findComponent(ParticleExpireIfInBlocks.class);
        var outside=definition.findComponent(ParticleExpireIfNotInBlocks.class);
        if((inside!=null&&inside.blocks().contains(id))||(outside!=null&&!outside.blocks().contains(id)))p.alive=false;
    }
}
