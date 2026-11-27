package com.tacz.probe;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.probe.mixin.NativeHurtBob;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Invoke native bobHurt after production mixins; compare actual poses, including independent gun-hit and death paths. */
final class PlatformHurtTilt {
    static void verify(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            var player=mc.player;var camera=mc.getCameraEntity();var type=mc.options.getCameraType();
            var item=player.getMainHandItem().copy();var config=RenderConfig.GUN_HURT_BOB_MULTIPLIER.get();
            var damage=mc.options.damageTiltStrength().get();
            int time=player.hurtTime,duration=player.hurtDuration,death=player.deathTime;float health=player.getHealth();
            var invoker=(NativeHurtBob)mc.gameRenderer;
            try {
                GunHurtBobTweak.resetTimestamp();mc.setCameraEntity(player);mc.options.setCameraType(CameraType.FIRST_PERSON);
                player.setHealth(20);player.hurtTime=7;player.hurtDuration=10;player.deathTime=0;
                var gun=GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).build();player.setItemInHand(InteractionHand.MAIN_HAND,gun);
                float direction=player.getHurtDir();float partial=.25f,progress=(7-partial)/10;
                float wave=Mth.sin(progress*progress*progress*progress*(float)Math.PI);
                for(double vanilla:new double[]{0,.4,1})for(double multiplier:new double[]{0,.1,1}) {
                    mc.options.damageTiltStrength().set(vanilla);RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(multiplier);
                    float angle=GunHurtBobTweak.heldGunTilt((float)(-wave*14*vanilla),direction,multiplier);
                    same(invoke(invoker,partial),ordinary(direction,angle),"first-person multiplier and native Damage Tilt");
                }
                mc.options.damageTiltStrength().set(1.0);RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(0.0);
                var vanilla=ordinary(direction,-wave*14);
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                same(invoke(invoker,partial),vanilla,"non-gun camera tilt unchanged");
                player.setItemInHand(InteractionHand.MAIN_HAND,gun);mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                same(invoke(invoker,partial),vanilla,"third-person camera tilt unchanged");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                player.setHealth(0);player.deathTime=8;
                var deathPose=new PoseStack();deathPose.mulPose(Axis.ZP.rotationDegrees(40-8000/(8+partial+200)));
                deathPose.mulPose(Axis.YP.rotationDegrees(-direction));deathPose.mulPose(Axis.ZP.rotationDegrees(-wave*14));deathPose.mulPose(Axis.YP.rotationDegrees(direction));
                same(invoke(invoker,partial),new Matrix4f(deathPose.last().pose()),"death roll and ordinary rotations unchanged");
                player.setHealth(health);player.deathTime=0;
                GunHurtBobTweak.markTimestamp(.4f);
                var gunHit=new PoseStack();gunHit.mulPose(Axis.YP.rotationDegrees(-direction*.4f));gunHit.mulPose(Axis.XP.rotationDegrees(-wave*.4f*14));gunHit.mulPose(Axis.YP.rotationDegrees(direction*.4f));
                same(invoke(invoker,partial),new Matrix4f(gunHit.last().pose()),"gun-hit data path unaffected by zero ordinary multiplier");
            } finally {
                GunHurtBobTweak.resetTimestamp();player.setHealth(health);player.hurtTime=time;player.hurtDuration=duration;player.deathTime=death;
                player.setItemInHand(InteractionHand.MAIN_HAND,item);mc.setCameraEntity(camera);mc.options.setCameraType(type);
                mc.options.damageTiltStrength().set(damage);RenderConfig.GUN_HURT_BOB_MULTIPLIER.set(config);
            }
        });
        System.out.println("PORT_PLATFORM_HURT_TILT: actual native bobHurt poses: ordinary multiplier 0/.1/1 x Damage Tilt 0/.4/1; non-gun, third-person, death roll and gun-hit-specific path preserved");
    }
    private static Matrix4f invoke(NativeHurtBob invoker,float partial){var pose=new PoseStack();invoker.platform$hurt(pose,partial);return new Matrix4f(pose.last().pose());}
    private static Matrix4f ordinary(float direction,float angle){var pose=new PoseStack();pose.mulPose(Axis.YP.rotationDegrees(-direction));pose.mulPose(Axis.ZP.rotationDegrees(angle));pose.mulPose(Axis.YP.rotationDegrees(direction));return new Matrix4f(pose.last().pose());}
    private static void same(Matrix4f actual,Matrix4f expected,String message){if(!actual.equals(expected,.00001f))throw new AssertionError(message+": "+actual+" != "+expected);}
    private PlatformHurtTilt(){}
}
