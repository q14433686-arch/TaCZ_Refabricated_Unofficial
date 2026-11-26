package com.tacz.probe;

import com.tacz.guns.client.render.scope.ScopePipRenderState;
import com.tacz.guns.client.render.scope.ScopePipRerender;
import com.tacz.guns.client.render.scope.ThermalReadback;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/** Same optical silhouette acceptance at actual model yaw, two ranges and all three scene paths. */
public final class ThermalPoseFrames {
    public static UUID watched;
    public static float renderedYaw = Float.NaN;

    static void verify(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos base) {
        boolean allow = ctx.computeOnClient(mc -> RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.get());
        double min = ctx.computeOnClient(mc -> RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION.get());
        try {
            ThermalFrames.kill(ctx,world);
            ThermalFrames.summon(ctx,world,base,"slime","Size:2,NoAI:1b,NoGravity:1b,Silent:1b");
            var id = world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().level()
                    .getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(base).inflate(40),
                            e -> e.getTags().contains("thermal_probe")).getFirst().getUUID());
            for (int mode=0;mode<3;mode++) {
                final boolean pip=mode>0, narrow=mode==2;
                ctx.runOnClient(mc -> {
                    RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(true);
                    RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION.set(1d);
                    RenderConfig.SCOPE_PIP_ENABLE.set(pip);
                    RenderConfig.SCOPE_PIP_RERENDER.set(narrow);
                });
                ctx.waitFor(mc -> ScopePipRenderState.suppressesWorldFovZoom(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false))==pip);
                if(narrow) ctx.waitFor(mc -> ScopePipRerender.hasScene());
                for (int range:new int[]{16,36}) {
                    // Keep exactly the same camera and background in each populated/empty pair.
                    move(ctx,world,id,base,range,12);
                    thermal(ctx,false);
                    String key=(narrow?"narrow":pip?"pip":"classic")+"_"+range;
                    var empty=ThermalFrames.frame(ctx,"thermal_"+key+"_empty");
                    move(ctx,world,id,base,range,0);
                    for(int yaw:new int[]{0,45,90}) {
                        pose(ctx,world,id,yaw);
                        thermal(ctx,false);
                        var optical=ThermalFrames.frame(ctx,"thermal_"+key+"_optical_"+yaw);
                        thermal(ctx,true);
                        var heat=ThermalFrames.frame(ctx,"thermal_"+key+"_heat_"+yaw);
                        var mask=ThermalReadback.read(ctx);
                        int[] coverage=ThermalFrames.coverage(optical,empty,heat);
                        ThermalFrames.wall(ctx,world,base,1);
                        var covered=ThermalReadback.read(ctx);
                        var behind=ThermalFrames.frame(ctx,"thermal_"+key+"_wall_"+yaw);
                        if(coverage[0]<12 || coverage[1]<.83*coverage[0] || mask.centralHot()<8
                                || covered.hot()!=0 || ThermalFrames.white(behind)>4)
                            throw new AssertionError("Thermal pose "+key+" yaw="+yaw+" drawn="+renderedYaw
                                    +" opticalBody="+coverage[1]+"/"+coverage[0]+" heat="+mask+" wall="+covered+" white="+ThermalFrames.white(behind));
                        System.out.println("PORT_THERMAL_POSE: "+key+" yaw="+yaw+" drawn="+renderedYaw
                                +" opticalBody="+coverage[1]+"/"+coverage[0]+" heat="+mask.centralHot()+" wall="+covered.hot());
                        ThermalFrames.wall(ctx,world,base,0);
                    }
                }
            }
        } finally {
            ctx.runOnClient(mc -> {
                watched=null;
                RenderConfig.SCOPE_PIP_ALLOW_SHADER_PACKS.set(allow);
                RenderConfig.SCOPE_PIP_MIN_MAGNIFICATION.set(min);
                RenderConfig.SCOPE_PIP_ENABLE.set(false);
                RenderConfig.SCOPE_PIP_RERENDER.set(false);
                RenderConfig.SCOPE_THERMAL_ENABLE.set(true);
            });
            ThermalFrames.wall(ctx,world,base,0);
            ThermalFrames.kill(ctx,world);
        }
    }
    private static void move(ClientGameTestContext ctx,TestSingleplayerContext world,UUID id,BlockPos base,int range,int x) {
        world.getServer().runCommand("tp "+id+" "+(base.getX()+.5+x)+" "+(base.getY()+1.2)+" "+(base.getZ()+.5+range));
        ctx.waitFor(mc -> mc.level.getEntity(id)!=null && Math.abs(mc.level.getEntity(id).getX()-(base.getX()+.5+x))<.05
                && Math.abs(mc.level.getEntity(id).getZ()-(base.getZ()+.5+range))<.05);
        ctx.waitTicks(6);
    }
    private static void thermal(ClientGameTestContext ctx,boolean on) {
        ctx.runOnClient(mc -> RenderConfig.SCOPE_THERMAL_ENABLE.set(on)); ctx.waitTicks(5);
    }
    private static void pose(ClientGameTestContext ctx,TestSingleplayerContext world,UUID id,int yaw) {
        world.getServer().runOnServer(s -> orient((LivingEntity)s.getPlayerList().getPlayers().getFirst().level().getEntity(id),yaw));
        ctx.runOnClient(mc -> { watched=id; renderedYaw=Float.NaN; orient((LivingEntity)mc.level.getEntity(id),yaw); });
        ctx.waitFor(mc -> Float.isFinite(renderedYaw) && Math.abs(Mth.wrapDegrees(renderedYaw-yaw))<1);
        ctx.waitTicks(4);
    }
    private static void orient(LivingEntity e,int yaw) {
        e.setYRot(yaw); e.setYHeadRot(yaw); e.yHeadRotO=e.yHeadRot=e.yBodyRotO=e.yBodyRot=yaw;
    }
    private ThermalPoseFrames() {}
}
