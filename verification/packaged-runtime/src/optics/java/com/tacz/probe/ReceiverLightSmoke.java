package com.tacz.probe;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFirePostEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.StatueBlock;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.render.light.SurfaceLights;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

/** Real local stock AK trigger -> server round consumed -> first main-world completed frame.
 * Synthetic events are used ONLY for the emitter-behind-an-opaque-wall negative. */
final class ReceiverLightSmoke {
    static void verify(ClientGameTestContext ctx,TestSingleplayerContext world,BlockPos base) {
        ctx.getInput().releaseKey(AimKey.AIM_KEY);
        ctx.waitFor(mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(0)<.001f);
        var id=Identifier.parse("tacz:ak47");
        var server=world.getServer();
        var saved=server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().copy());
        boolean[] old=ctx.computeOnClient(mc -> new boolean[]{RenderConfig.MUZZLE_SURFACE_LIGHT.get(),RenderConfig.LEGACY_MUZZLE_LIGHT.get(),
                RenderConfig.SCOPE_PIP_ENABLE.get(),mc.options.bobView().get()});
        var eye=ctx.computeOnClient(mc -> mc.player.getEyePosition());
        var origin=BlockPos.containing(eye);
        var cowPos=eye.add(2.35,-.60,4);var standPos=eye.add(1.1,-.89,3.6);var dropPos=eye.add(.10,.72,3.7);
        var board=origin.offset(-2,1,4);var cabinet=origin.offset(-1,1,4);var statue=origin.offset(-3,-1,4);
        var ids=server.computeOnServer(s -> {
            var p=s.getPlayerList().getPlayers().getFirst();var level=p.level();
            for(int x=-8;x<=8;x++) for(int y=-2;y<=5;y++) for(int z=-3;z<=30;z++) {
                var block=Math.abs(x)==8 || y==-2 || y==5 || z==-3 || z==5 || z==30 ? Blocks.GRAY_CONCRETE : Blocks.AIR;
                level.setBlock(origin.offset(x,y,z),block.defaultBlockState(),Block.UPDATE_ALL);
            }
            var cow=EntityType.COW.create(level,EntitySpawnReason.COMMAND);
            cow.snapTo(cowPos,0,0);cow.setNoAi(true);cow.setNoGravity(true);cow.setInvulnerable(true);level.addFreshEntity(cow);
            var stand=EntityType.ARMOR_STAND.create(level,EntitySpawnReason.COMMAND);
            stand.snapTo(standPos,0,0);stand.setNoGravity(true);stand.setShowArms(true);stand.setInvulnerable(true);
            stand.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));level.addFreshEntity(stand);
            var drop=new ItemEntity(level,dropPos.x,dropPos.y,dropPos.z,new ItemStack(Items.DIAMOND),0,0,0);
            drop.setNeverPickUp();drop.setNoGravity(true);level.addFreshEntity(drop);
            level.setBlock(board,ModBlocks.GUN_PEGBOARD.defaultBlockState(),Block.UPDATE_ALL);
            level.setBlock(cabinet,ModBlocks.GUN_DISPLAY_CASE.defaultBlockState().setValue(GunDisplayCaseBlock.LOCKED,false),Block.UPDATE_ALL);
            for(var rack:List.of(board,cabinet)) if(!((DisplayRackBlockEntity)level.getBlockEntity(rack)).setItem(10,new ItemStack(Items.EMERALD)))
                throw new AssertionError("Receiver fixture rack refused emerald");
            var lower=ModBlocks.STATUE.defaultBlockState();level.setBlock(statue,lower,Block.UPDATE_ALL);
            level.setBlock(statue.above(),lower.setValue(StatueBlock.HALF,DoubleBlockHalf.UPPER),Block.UPDATE_ALL);
            p.setItemInHand(InteractionHand.MAIN_HAND,GunItemBuilder.create().setId(id).setAmmoCount(8).setAmmoInBarrel(true).setFireMode(FireMode.SEMI).build());
            p.inventoryMenu.broadcastChanges();return List.of(cow.getUUID(),stand.getUUID(),drop.getUUID());
        });
        try {
            ctx.runOnClient(mc -> { RenderConfig.LEGACY_MUZZLE_LIGHT.set(true);RenderConfig.SCOPE_PIP_ENABLE.set(false);mc.options.bobView().set(false);
                LightFrames.setup(cowPos,standPos,dropPos,statue,board,cabinet); });
            server.runCommand("time set 18000");PlayerProbe.turn(ctx,0,0);
            ctx.waitFor(mc -> ids.stream().allMatch(uuid -> mc.level.getEntity(uuid)!=null) && mc.player.getMainHandItem().getItem() instanceof IGun gun
                    && gun.getGunId(mc.player.getMainHandItem()).equals(id) && mc.level.getBlockEntity(cabinet) instanceof DisplayRackBlockEntity rack && rack.getItem(10).is(Items.EMERALD));
            world.getClientWorld().waitForChunksRender();ctx.waitTicks(40);
            ctx.waitFor(mc -> IGunOperator.fromLivingEntity(mc.player).getSynDrawCoolDown()==0 && IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder().clientBaseTimestamp>=0);
            var rest=LightFrames.still(ctx,"rest");
            var off=fire(ctx,world,false);var on=fire(ctx,world,true);
            ctx.waitFor(mc -> SurfaceLights.diagnostics().active()==0);PlayerProbe.turn(ctx,0,0);ctx.waitTicks(22);
            var expired=LightFrames.still(ctx,"expired");
            var barrier=origin.north();
            server.runOnServer(s -> {var level=s.getPlayerList().getPlayers().getFirst().level();
                for(int x=-4;x<=4;x++) for(int y=-1;y<=3;y++) level.setBlock(barrier.offset(x,y,0),Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
            });
            ctx.waitFor(mc -> mc.level.getBlockState(barrier).is(Blocks.STONE));world.getClientWorld().waitForChunksRender();ctx.waitTicks(10);
            var blockedRest=LightFrames.still(ctx,"blocked_rest");
            var future=ctx.computeOnClient(mc -> {
                var f=LightFrames.arm("blocked",true);
                var emitter=EntityType.COW.create(mc.level,EntitySpawnReason.COMMAND);emitter.setId(100000123);
                emitter.snapTo(new Vec3(eye.x,eye.y-emitter.getEyeHeight(),origin.getZ()-2.1),0,0);
                GunFirePostEvent.EVENT.invoker().post(new GunFirePostEvent(emitter,GunItemBuilder.create().setId(id).build(),cn.sh1rocu.tacz.api.LogicalSide.CLIENT));
                return f;
            });
            var blocked=LightFrames.read(ctx,future);
            inputs(off,on,expired,blockedRest,blocked);
            var probes=List.of(new Probe("cow",cowPos.add(0,.99,-.4),.075,16),new Probe("stand",standPos.add(0,1.41,-.09),.035,16),
                    new Probe("helmet",standPos.add(0,1.76,-.25),.066,16),new Probe("statue",Vec3.atLowerCornerOf(statue).add(.50,2.19,.22),.065,16),
                    new Probe("board",Vec3.atLowerCornerOf(board).add(.22,.25,.8125),.075,16),
                    new Probe("case_dark_back",Vec3.atLowerCornerOf(cabinet).add(.23,.25,.875),.045,16),
                    new Probe("case_lower_frame",Vec3.atLowerCornerOf(cabinet).add(.50,.03125,.25),.018,16),
                    new Probe("board_item",Vec3.atLowerCornerOf(board).add(.625,.625,.75),.035,8),
                    new Probe("fullbright_case_item",Vec3.atLowerCornerOf(cabinet).add(.625,.625,.75),.035,8),
                    new Probe("near_wall",new Vec3(eye.x+.2,eye.y+1.0,origin.getZ()+5),.12,16),
                    new Probe("near_floor",new Vec3(eye.x+2,origin.getY()-1,eye.z+3),.12,16,true));
            var error=new StringBuilder();
            for(var probe:probes) {
                int[] a=List.of(rest,off,on,expired,blockedRest,blocked).stream().mapToInt(probe::value).toArray();
                int light=a[2]-a[1],expiry=a[3]-a[0],wall=a[5]-a[4];
                // The real case back albedo is only 37/255. In the HDR shader its
                // native 23-code response becomes five display codes (4 -> 9). Test
                // material-relative contrast plus >=4x measured control noise there,
                // AND independently require the normal >=6-code response on its frame.
                // Zero response (the former overwritten pass) fails both criteria.
                boolean dark=probe.name.equals("case_dark_back");
                int noise=Math.max(Math.abs(expiry),Math.abs(wall));
                boolean failed=probe.name.startsWith("fullbright") ? Math.abs(light)>7 : dark
                        ? light<Math.max(4,4*noise) || a[2]<2*a[1] || noise>2 : light<6;
                if(failed || Math.abs(expiry)>7 || Math.abs(wall)>7)
                    error.append(probe.name).append(Arrays.toString(a)).append(' ');
                System.out.println("PORT_RECEIVER_PIXEL: "+probe.name+" rest/off/on/expired/blocked-rest/blocked="+Arrays.toString(a)+" delta="+light+"/"+expiry+"/"+wall);
            }
            if(!error.isEmpty()) throw new AssertionError("Receiver pixels: "+error+" "+LightFrames.sourceDebug+" case quads="+LightFrames.reflectionDebug+" grid="+grid(off,on,Vec3.atLowerCornerOf(cabinet).add(0,0,.875)));
        } finally {
            ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);
            ctx.runOnClient(mc -> { LightFrames.clear();RenderConfig.MUZZLE_SURFACE_LIGHT.set(old[0]);RenderConfig.LEGACY_MUZZLE_LIGHT.set(old[1]);
                RenderConfig.SCOPE_PIP_ENABLE.set(old[2]);mc.options.bobView().set(old[3]);SurfaceLights.clear(); });
            server.runOnServer(s -> {var p=s.getPlayerList().getPlayers().getFirst();var level=p.level();
                for(var uuid:ids) if(level.getEntity(uuid)!=null) level.getEntity(uuid).discard();
                for(var pos:List.of(board,cabinet)) if(level.getBlockEntity(pos) instanceof DisplayRackBlockEntity r) r.clearContent();
                for(var pos:List.of(board,cabinet,statue,statue.above())) level.removeBlock(pos,false);
                p.setItemInHand(InteractionHand.MAIN_HAND,saved);p.inventoryMenu.broadcastChanges(); });
            server.runCommand("time set 6000");PlayerProbe.turn(ctx,0,0);ctx.waitTicks(20);
        }
    }
    private static String grid(LightFrames.Frame off,LightFrames.Frame on,Vec3 corner) {
        var text=new StringBuilder();
        for(int y=1;y<=9;y++) { text.append('[');for(int x=1;x<=9;x++) {
            var p=corner.add(x*.1,y*.1,0);text.append(((on.rgb(p)>>16)&255)-((off.rgb(p)>>16)&255)).append(',');
        } text.append(']'); }
        return text.toString();
    }
    private static LightFrames.Frame fire(ClientGameTestContext ctx,TestSingleplayerContext world,boolean enabled) {
        PlayerProbe.turn(ctx,0,0);ctx.waitTicks(12);
        var future=ctx.computeOnClient(mc -> { RenderConfig.MUZZLE_SURFACE_LIGHT.set(enabled);return LightFrames.arm(enabled?"on":"off",false); });
        int ammo=world.getServer().computeOnServer(s -> {var stack=s.getPlayerList().getPlayers().getFirst().getMainHandItem();return ((IGun)stack.getItem()).getCurrentAmmoCount(stack);});
        ctx.getInput().holdKey(ShootKey.SHOOT_KEY);
        try { ctx.waitFor(mc -> LightFrames.shots>0); } finally {ctx.getInput().releaseKey(ShootKey.SHOOT_KEY);}
        var frame=LightFrames.read(ctx,future);
        PlayerProbe.waitForServer(ctx,world,s -> {var stack=s.getPlayerList().getPlayers().getFirst().getMainHandItem();return ((IGun)stack.getItem()).getCurrentAmmoCount(stack)==ammo-1;});
        return frame;
    }
    private static void inputs(LightFrames.Frame off,LightFrames.Frame on,LightFrames.Frame expired,LightFrames.Frame base,LightFrames.Frame wall) {
        if(LightFrames.exceptionRestores!=1) throw new AssertionError("Nested exception path was not exercised");
        for(int i=0;i<6;i++) {
            int a=off.light()[i],b=on.light()[i];
            if(a<0 || b<0 || a!=expired.light()[i] || base.light()[i]!=wall.light()[i] || wall.light()[i]<0
                    || (i==5 ? a!=LightTexture.FULL_BRIGHT || a!=b : LightTexture.block(b)-LightTexture.block(a)<3 || LightTexture.sky(a)!=LightTexture.sky(b)))
                throw new AssertionError("Receiver UV2 "+i+" OFF="+off+" ON="+on+" expired="+expired+" wall="+base+"/"+wall);
        }
        System.out.println("PORT_RECEIVER_INPUT: OFF="+off+" ON="+on+" no sky change, expiry/wall leaks; original submit inputs and nested exception context restored");
    }
    private record Probe(String name,Vec3 center,double extent,int shift,boolean horizontal) {
        Probe(String name,Vec3 center,double extent,int shift) { this(name,center,extent,shift,false); }
        int value(LightFrames.Frame f) {return f.mean(center,extent,shift,horizontal);}
    }
    private ReceiverLightSmoke() {}
}
