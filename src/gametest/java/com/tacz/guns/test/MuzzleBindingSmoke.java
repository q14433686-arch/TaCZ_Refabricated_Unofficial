package com.tacz.guns.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import com.tacz.guns.test.mixin.MuzzleSnapshotInvoker;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Freeze simulation during two controlled model extractions. Both particles came from one
 * funded F5 shot; this changes the model root, not the shooter, camera, or particle velocity. */
final class MuzzleBindingSmoke {
    static void check(Minecraft mc, ParticleInstance flash, ParticleInstance smoke) {
        if (flash==null || smoke==null || !flash.alive || !smoke.alive || !flash.worldSpace || !smoke.worldSpace)
            throw new AssertionError("Need live F5 particles for anchor comparison");
        var stack=mc.player.getMainHandItem(); var id=((IGun)stack.getItem()).getGunId(stack);
        var model=TimelessAPI.getGunDisplay(stack).orElseThrow().getGunModel();
        int owner=mc.player.getId(); var rt=WorldEmitterManager.getInstance();
        var initial=new Matrix4f(flash.emitter.getWorldTransform());
        var smokeBirth=new Matrix4f(smoke.emitter.getWorldTransform());
        var beforeSmoke=position(smoke);
        PoseStack root=new PoseStack(); root.translate(1,2,-3); root.rotate(com.mojang.math.Axis.YP.rotationDegrees(40));
        try {
            rt.updateAnchors(owner,id,model,root,true,null,0); // A hand extraction must not rebind a world shot.
            if (!initial.equals(flash.emitter.getWorldTransform(),1e-5f)) throw new AssertionError("Cross-view flash rebind");
            rt.updateAnchors(owner,id,model,root,false,null,0);
            PoseStack bone=new PoseStack(); bone.last().pose().set(root.last().pose());
            for(var part:model.getMuzzleFlashPosPath())part.translateAndRotateAndScale(bone);
            var camera=mc.gameRenderer.mainCamera().position();
            var boneWorld=new Matrix4f().translation((float)camera.x,(float)camera.y,(float)camera.z).mul(bone.last().pose());
            var relative=boneWorld.invert().mul(flash.emitter.getWorldTransform());
            if(!relative.equals(new Matrix4f().translation(0,0,-7.4f/16),.001f))
                throw new AssertionError("Did not retain RC1's local -7.4/16 offset on refresh: " + relative);
            var start=position(flash); root.last().pose().m30(root.last().pose().m30()+1.25f);
            rt.updateAnchors(owner,id,model,root,false,null,0);
            var travel=position(flash).sub(start);
            if(!travel.equals(new Vector3f(1.25f,0,0),.001f) || beforeSmoke.distance(position(smoke))>.001f
                    || !smokeBirth.equals(smoke.emitter.getWorldTransform(),1e-5f))
                throw new AssertionError("Expected only flash to follow: delta="+travel+", smoke="+position(smoke).sub(beforeSmoke));
            GunMod.LOGGER.info("PORT_MUZZLE_ANCHOR: RC1 local Z={}, root +1.25: drawn flash dx={}, smoke dx={}; hand extraction did not rebind F5",relative.m32(),travel.x,position(smoke).x-beforeSmoke.x);
        } finally { flash.emitter.setEmitterTransform(initial,initial); }
    }

    private static Vector3f position(ParticleInstance live) {
        var draw=MuzzleSnapshotInvoker.taczTest$freeze(live);
        return new Vector3f(draw.x,draw.y,draw.z);
    }
}
