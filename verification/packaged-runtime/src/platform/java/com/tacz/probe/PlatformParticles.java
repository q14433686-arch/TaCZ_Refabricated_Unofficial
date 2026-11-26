package com.tacz.probe;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.*;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.client.particle.AnimationParticleDispatcher;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.resources.Identifier;
import java.util.List;

/** An authored keyframe on the held gun's real controller, consumed by its next posed submit. */
final class PlatformParticles {
    private static final int TRACK=31;
    private static final class Fixture extends ObjectAnimation {
        Fixture(String name,String locator) {
            super(name);
            setParticleChannel(new ObjectAnimationParticleChannel(List.of(new ObjectAnimationParticleChannel.Keyframe(0,
                    List.of(new AnimationParticleEvent(Identifier.parse("minecraft:smoke"),locator,null))))));
            includeDuration(1);
        }
    }
    static void verify(ClientGameTestContext ctx) {
        var controller=ctx.computeOnClient(mc->TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow().getAnimationStateMachine().getAnimationController());
        long before=ctx.computeOnClient(mc->AnimationParticleDispatcher.submitted());
        try {
            ctx.runOnClient(mc->{
                controller.providePrototypeIfAbsent("port_particle_locator",()->new Fixture("port_particle_locator",GunModelConstant.MUZZLE_FLASH_ORIGIN_NODE));
                controller.runAnimation(TRACK,"port_particle_locator",ObjectAnimation.PlayType.PLAY_ONCE_HOLD,0);
                controller.getAnimation(TRACK).update(false);
            });
            ctx.waitFor(mc->AnimationParticleDispatcher.submitted()==before+1,100);
            ctx.waitTicks(6);
            ctx.runOnClient(mc->{
                if(AnimationParticleDispatcher.submitted()!=before+1)throw new AssertionError("One animation keyframe replayed across submissions");
                controller.removeAnimation(TRACK);
                controller.providePrototypeIfAbsent("port_particle_missing_locator",()->new Fixture("port_particle_missing_locator","port_missing_locator"));
                controller.runAnimation(TRACK,"port_particle_missing_locator",ObjectAnimation.PlayType.PLAY_ONCE_HOLD,0);
                controller.getAnimation(TRACK).update(false);
            });
            ctx.waitTicks(6);
            ctx.runOnClient(mc->{
                if(AnimationParticleDispatcher.submitted()!=before+1 || !controller.getAnimation(TRACK).consumeParticleEvents().isEmpty())
                    throw new AssertionError("Missing locator spawned substitute particles or keyframe never reached posed submit");
            });
            System.out.println("PORT_PLATFORM_PARTICLES: real held-gun animation keyframe reached posed locator once; subsequent frames did not replay it; missing locator consumed without substitute");
        } finally {ctx.runOnClient(mc->controller.removeAnimation(TRACK));}
    }
    private PlatformParticles(){}
}
