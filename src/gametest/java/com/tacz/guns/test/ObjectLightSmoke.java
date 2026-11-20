package com.tacz.guns.test;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.StatueBlock;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import com.tacz.guns.client.render.light.SurfaceLights;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.test.mixin.ReimaginedShotControl;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

import static com.tacz.guns.test.SurfaceLightFrames.*;

/** Local stock AK trigger -> server round consumed -> first main-world frame. All probes
 * are on the receiver, above the muzzle and in front of a distant (out of range) backstop. */
final class ObjectLightSmoke {
    private static final Identifier AK = Identifier.parse("tacz:ak47");
    private enum Response { LIT, FULLBRIGHT, UNSHADED }
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos origin, boolean measureLoad) {
        var eye = ctx.computeOnClient(mc->mc.player.getEyePosition());
        Vec3 cowPos = eye.add(2.35,-.60,4), standPos = eye.add(1.1,-.89,3.6);
        var dropPos = eye.add(.10,.72,3.7);
        var board = origin.offset(-2,1,4); var cabinet = origin.offset(-1,1,4); var statue = origin.offset(-3,-1,4);
        var server=world.getServer();
        int cutoff=ctx.computeOnClient(mc->RenderConfig.DROPPED_GUN_SPRITE_DISTANCE.get());
        var ids=server.computeOnServer(s->{
            var p=s.getPlayerList().getPlayers().getFirst(); var level=p.level();
            var cow=new Cow(EntityTypes.COW,level); cow.snapTo(cowPos,0,0); cow.setNoAi(true); cow.setNoGravity(true); level.addFreshEntity(cow);
            var stand=new ArmorStand(level,standPos.x,standPos.y,standPos.z); stand.setNoGravity(true); stand.setShowArms(true);
            stand.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET)); level.addFreshEntity(stand);
            var proxy=GunItemBuilder.create().setId(AK).build(); ((IGun)proxy.getItem()).setGunDisplayId(proxy,Identifier.parse("port_test:drop_no_lod"));
            var drop=new ItemEntity(level,dropPos.x,dropPos.y,dropPos.z,proxy,0,0,0); drop.setNeverPickUp(); drop.setNoGravity(true); level.addFreshEntity(drop);
            level.setBlock(board,ModBlocks.GUN_PEGBOARD.defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(cabinet,ModBlocks.GUN_DISPLAY_CASE.defaultBlockState().setValue(GunDisplayCaseBlock.LOCKED,false),Block.UPDATE_ALL);
            for(var rack:List.of(board,cabinet)) if (!((DisplayRackBlockEntity)level.getBlockEntity(rack)).setItem(10,new ItemStack(Items.EMERALD))) throw new AssertionError("Fixture whitelist refused rack item");
            var lower=ModBlocks.STATUE.defaultBlockState(); level.setBlock(statue,lower,Block.UPDATE_ALL);
            level.setBlock(statue.above(),lower.setValue(StatueBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_ALL);
            p.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(AK).setAmmoCount(8).setAmmoInBarrel(true).setFireMode(FireMode.SEMI).build());
            p.inventoryMenu.broadcastChanges(); return List.of(cow.getUUID(),stand.getUUID(),drop.getUUID());
        });
        try {
            ctx.runOnClient(mc->{RenderConfig.DROPPED_GUN_SPRITE_DISTANCE.set(0);ReceiverInputs.scene(cowPos,standPos,dropPos,statue,board,cabinet);});
            ctx.waitFor(mc->ids.stream().allMatch(id->mc.level.getEntity(id)!=null) && mc.level.getBlockEntity(cabinet) instanceof DisplayRackBlockEntity r
                    && r.getItem(10).is(Items.EMERALD) && mc.level.getBlockEntity(board) instanceof DisplayRackBlockEntity b && b.getItem(10).is(Items.EMERALD) && mc.player.getMainHandItem().getItem() instanceof IGun);
            world.getClientLevel().waitForChunksRender();ctx.getInput().lookAt(0,0);ctx.waitTicks(30);
            ctx.waitFor(mc->IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown()==0 && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientBaseTimestamp>=0);
            var rest=still(ctx,"objects_rest");
            var off=SurfaceLightSmoke.fireLocal(ctx,world,false,"objects"); var on=SurfaceLightSmoke.fireLocal(ctx,world,true,"objects");
            ctx.waitFor(mc->SurfaceLights.diagnostics().active()==0); ctx.getInput().lookAt(0,0); ctx.waitTicks(22);
            var expired=still(ctx,"objects_expired");
            // Same receivers, no scenery change in the view. An emitter behind a stone
            // partition BEHIND the camera cannot contribute to their front faces.
            var barrier=origin.north(1);
            server.runOnServer(s->{var l=s.getPlayerList().getPlayers().getFirst().level();for(int x=-4;x<=4;x++)for(int y=-1;y<=3;y++)l.setBlock(barrier.offset(x,y,0),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);});
            ctx.waitFor(mc->mc.level.getBlockState(barrier).is(Blocks.STONE)); world.getClientLevel().waitForChunksRender();ctx.waitTicks(10);
            var blockedRest=still(ctx,"objects_blocked_rest");
            ctx.runOnClient(mc->{var emitter=new Cow(EntityTypes.COW,mc.level);emitter.setId(100000123);
                emitter.snapTo(new Vec3(eye.x,eye.y-emitter.getEyeHeight(),origin.getZ()-2.1),0,0);
                arm("objects_blocked",Trigger.STILL); ReimaginedShotControl.taczTest$shot(new GunFirePostEvent(emitter,GunItemBuilder.create().setId(AK).build(),LogicalSide.CLIENT));});
            var blocked=read(ctx);
            ctx.runOnClient(mc->ReceiverInputs.verify());
            var probes=List.of(new Probe("cow",cowPos.add(0,.99,-.4),.075,16),new Probe("stand wood",standPos.add(0,1.41,-.09),.035,16),
                    new Probe("iron helmet",standPos.add(0,1.76,-.25),.066,16),new Probe("statue head",Vec3.atLowerCornerOf(statue).add(.50,2.19,.22),.065,16),
                    new Probe("board plate",Vec3.atLowerCornerOf(board).add(.22,.25,.8125),.075,16),new Probe("case back",Vec3.atLowerCornerOf(cabinet).add(.23,.25,.875),.045,16),
                    new Probe("board item",Vec3.atLowerCornerOf(board).add(.625,.625,.75),.035,8),
                    new Probe("case item (fullbright)",Vec3.atLowerCornerOf(cabinet).add(.625,.625,.75),.035,8));
            var errors=new StringBuilder();
            boolean flat=Boolean.getBoolean("tacz.test.iris") && System.getProperty("tacz.test.irisColor")==null;
            for (var probe:probes) {
                var samples=List.of(rest,off,on,expired,blockedRest,blocked).stream().mapToInt(probe::value).toArray();
                boolean emissive=probe.name.startsWith("case item");
                boolean unlitShader=flat && !probe.name.equals("board plate") && !probe.name.equals("case back");
                check(probe.name,samples,emissive ? Response.FULLBRIGHT : unlitShader ? Response.UNSHADED : Response.LIT,errors);
            }
            var drops=List.of(rest,off,on,expired,blockedRest,blocked).stream().mapToInt(f->cyanCore(f,dropPos)).toArray();
            check("dropped TaCZ item",drops,flat ? Response.UNSHADED : Response.LIT,errors);
            if (!errors.isEmpty()) throw new AssertionError("World receiver controls failed: "+errors);
            if (measureLoad) SurfaceLightSmoke.timeOccupiedScene(ctx, origin);
            GunMod.LOGGER.info("PORT_RECEIVER: complete, flat unlit test shader={}",flat);
        } finally {
            ctx.runOnClient(mc->{ReceiverInputs.clear();RenderConfig.DROPPED_GUN_SPRITE_DISTANCE.set(cutoff);});
            server.runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();var l=p.level();for(var id:ids) if(l.getEntity(id)!=null) l.getEntity(id).discard();
                for(var pos:List.of(board,cabinet)) if(l.getBlockEntity(pos) instanceof DisplayRackBlockEntity r) r.clearContent();
                for (var pos:List.of(board,cabinet,statue.above(),statue)) l.removeBlock(pos,false);
                for(int x=-4;x<=4;x++)for(int y=-1;y<=3;y++)l.removeBlock(origin.offset(x,y,-1),false);
                p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.inventoryMenu.broadcastChanges();});
            ctx.waitFor(mc->ids.stream().noneMatch(id->mc.level.getEntity(id)!=null));
        }
    }
    private record Probe(String name,Vec3 p,double extent,int shift) {
        int value(Frame f) { return f.mean(Region.wall(p),extent,shift,20); }
    }
    /** The actual rotating/bobbing ItemEntity is not in a fixed screen rectangle. Use only its
     * cyan test albedo in this projected window, never the grey wall/skin/muzzle. */
    private static int cyanCore(Frame frame,Vec3 base) {
        int[] values=new int[17*29]; int n=0;
        for(int y=0;y<29;y++)for(int x=-8;x<=8;x++) {
            int rgb=frame.rgb(base.add(x*.035,-.25+y*.05,0)); int g=rgb>>8&255,b=rgb&255,r=rgb>>16&255;
            if(Math.min(g,b)>r+2) values[n++]=g;
        }
        if(n<12) throw new AssertionError("Lost dropped item cyan core: "+n+" samples");
        Arrays.sort(values,0,n);return values[n/2];
    }
    private static void check(String name,int[] a,Response response,StringBuilder errors) {
        int light=a[2]-a[1],expired=a[3]-a[0],blocked=a[5]-a[4];
        GunMod.LOGGER.info("PORT_RECEIVER: {} rest/off/on/expired/blocked-rest/blocked={} delta={}/{}/{}",name,Arrays.toString(a),light,expired,blocked);
        if ((response==Response.LIT && light<6) || (response==Response.FULLBRIGHT && Math.abs(light)>7) || Math.abs(expired)>7 || Math.abs(blocked)>7) errors.append(name).append(Arrays.toString(a)).append(' ');
    }
    private ObjectLightSmoke() {}
}
