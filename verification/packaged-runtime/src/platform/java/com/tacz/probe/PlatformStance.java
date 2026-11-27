package com.tacz.probe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.config.client.ZoomConfig;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import com.tacz.guns.util.VariantHelper;
import com.tacz.guns.util.math.MathUtil;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Arrays;

/** Real entity stance selection on both sides and the actual merged native mouse wrapper.
 * The wrapper's continuation calls LocalPlayer.turn; this is not a physical cursor callback test. */
final class PlatformStance {
    static void verify(ClientGameTestContext ctx, TestSingleplayerContext world) {
        world.getServer().runOnServer(server -> stance(server.getPlayerList().getPlayers().getFirst()));
        ctx.runOnClient(mc -> { stance(mc.player); mouse(mc); });
        System.out.println("PORT_PLATFORM_STANCE: actual client/server entities: airborne override move/stand before aim/prone/sneak, grounded and disabled priority retained; merged native mouse wrapper configurable up/down/90-degree limits, inward recovery, swimming/non-gun exemptions and tagged variant sensitivity passed (continuation calls native LocalPlayer.turn; not a physical cursor callback claim)");
    }

    private static void stance(LivingEntity player) {
        boolean enabled=SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.get(), ground=player.onGround();
        var pose=player.getPose();boolean swimming=player.isSwimming();
        float aiming=IGunOperator.fromLivingEntity(player).getSynAimingProgress();
        try {
            for(boolean override:new boolean[]{false,true})for(boolean grounded:new boolean[]{false,true})
                for(var stance:new Pose[]{Pose.STANDING,Pose.CROUCHING,Pose.SWIMMING})for(float aim:new float[]{0,1}) {
                    SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.set(override);
                    player.setOnGround(grounded);player.setSwimming(false);player.setPose(stance);
                    ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(player,aim);
                    for(double move:new double[]{0,.05f,.051}) {
                        var expected=override&&!grounded ? (move>.05f?InaccuracyType.MOVE:InaccuracyType.STAND)
                                :aim==1?InaccuracyType.AIM:stance==Pose.SWIMMING?InaccuracyType.LIE:stance==Pose.CROUCHING?InaccuracyType.SNEAK
                                :move>.05f?InaccuracyType.MOVE:InaccuracyType.STAND;
                        var actual=InaccuracyType.getInaccuracyType(player,()->move);
                        if(actual!=expected)throw new AssertionError("Actual entity stance "+actual+" != "+expected+" override="+override+" grounded="+grounded+" pose="+stance+" aim="+aim+" move="+move);
                    }
                }
        } finally {
            SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.set(enabled);
            player.setOnGround(ground);player.setSwimming(swimming);player.setPose(pose);
            ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(player,aiming);
        }
    }

    private static void mouse(Minecraft mc) {
        var p=mc.player;var oldStack=p.getMainHandItem();var pose=p.getPose();boolean swimming=p.isSwimming();
        float x=p.getXRot(),y=p.getYRot(),oldX=p.xRotO,oldY=p.yRotO,aim=IGunOperator.fromLivingEntity(p).getSynAimingProgress();
        double up=SyncConfig.CRAWL_MAX_UP_ANGLE.get(),down=SyncConfig.CRAWL_MAX_DOWN_ANGLE.get();
        double base=ZoomConfig.ZOOM_SENSITIVITY_BASE_MULTIPLIER.get();
        try {
            Method wrapper=Arrays.stream(mc.mouseHandler.getClass().getDeclaredMethods())
                    .filter(m->m.getReturnType()==void.class && Arrays.equals(m.getParameterTypes(),new Class<?>[]{LocalPlayer.class,double.class,double.class,Operation.class}))
                    .reduce((a,b)->{throw new AssertionError("Ambiguous native mouse wrapper signature");}).orElseThrow();
            wrapper.setAccessible(true);
            var gunStack=GunItemBuilder.create().setId(Identifier.parse("tacz:ak47")).build();
            p.setItemInHand(InteractionHand.MAIN_HAND,gunStack);p.setSwimming(false);p.setPose(Pose.SWIMMING);
            ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(p,0f);
            for(double[] limits:new double[][]{{45,30},{10,5},{0,0},{90,90}}) {
                SyncConfig.CRAWL_MAX_UP_ANGLE.set(limits[0]);SyncConfig.CRAWL_MAX_DOWN_ANGLE.set(limits[1]);
                for(float pitch:new float[]{-89,-46,-11,-1,0,1,6,31,89})for(double delta:new double[]{-2,2}) {
                    p.setXRot(pitch);p.setYRot(0);
                    double[] result=turn(mc,wrapper,1,delta);
                    double expected=(-pitch>limits[0]&&delta<0)||(-pitch < -limits[1]&&delta>0)?0:delta;
                    near(result[0],1,"Crawl changed yaw");near(result[1],expected,"Crawl pitch limit/recovery");
                    near(p.getXRot(),Math.max(-90,Math.min(90,pitch+expected*.15)),"Native turn continuation pitch");
                }
            }
            SyncConfig.CRAWL_MAX_UP_ANGLE.set(10d);SyncConfig.CRAWL_MAX_DOWN_ANGLE.set(5d);
            p.setXRot(-80);p.setSwimming(true);
            near(turn(mc,wrapper,1,-2)[1],-2,"Swimming incorrectly clamped");
            p.setSwimming(false);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setXRot(-80);
            near(turn(mc,wrapper,1,-2)[1],-2,"Non-gun incorrectly clamped");

            p.setPose(Pose.STANDING);p.setItemInHand(InteractionHand.MAIN_HAND,gunStack);
            var attachment=AttachmentItemBuilder.create().setId(Identifier.parse("tacz:scope_acog_ta31")).build();
            VariantHelper.setVariant(attachment,Identifier.parse("tacz_lang_probe:replace"));
            ((IAttachment)attachment.getItem()).setZoomNumber(attachment,-1);
            var gun=(IGun)gunStack.getItem();gun.installAttachment(gunStack,attachment);
            if(!gun.getAttachmentId(gunStack,AttachmentType.SCOPE).equals(Identifier.parse("tacz:scope_acog_ta31")))throw new AssertionError("Mouse scope fixture did not install");
            near(gun.getAimingZoom(gunStack),8,"Variant zoom fixture");
            ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(p,1f);ZoomConfig.ZOOM_SENSITIVITY_BASE_MULTIPLIER.set(1d);
            double fov=mc.options.fov().get();double expected=MathUtil.zoomSensitivityRatio(MathUtil.magnificationToFov(8,fov),fov,ZoomConfig.SCREEN_DISTANCE_COEFFICIENT.get());
            var result=turn(mc,wrapper,2,3);near(result[0],2*expected,"Variant yaw sensitivity");near(result[1],3*expected,"Variant pitch sensitivity");
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
        finally {
            SyncConfig.CRAWL_MAX_UP_ANGLE.set(up);SyncConfig.CRAWL_MAX_DOWN_ANGLE.set(down);ZoomConfig.ZOOM_SENSITIVITY_BASE_MULTIPLIER.set(base);
            p.setItemInHand(InteractionHand.MAIN_HAND,oldStack);p.setSwimming(swimming);p.setPose(pose);p.setXRot(x);p.setYRot(y);p.xRotO=oldX;p.yRotO=oldY;
            ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(p,aim);
        }
    }
    private static double[] turn(Minecraft mc,Method wrapper,double yaw,double pitch) throws ReflectiveOperationException {
        var result=new double[]{Double.NaN,Double.NaN};
        Operation<Void> original=args->{result[0]=(double)args[1];result[1]=(double)args[2];((LocalPlayer)args[0]).turn(result[0],result[1]);return null;};
        wrapper.invoke(mc.mouseHandler,mc.player,yaw,pitch,original);
        return result;
    }
    private static void near(double actual,double expected,String label){if(!Double.isFinite(actual)||Math.abs(actual-expected)>.0001)throw new AssertionError(label+": "+actual+" != "+expected);}
    private PlatformStance(){}
}
