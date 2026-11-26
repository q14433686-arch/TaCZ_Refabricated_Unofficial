package com.tacz.probe;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.util.AmmoBlockConfigRead;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.event.ammo.DestroyGlassBlock;
import com.tacz.guns.util.EntityUtil;
import com.tacz.guns.util.block.BlockRayTrace;
import com.tacz.guns.util.block.ProjectileExplosion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Real production-server ray selection, block hit consumer, and explosion filtering. */
public final class AmmoPolicySmoke {
    public static void blockPolicies(GameTestHelper h) {
        var level=h.getLevel();var pos=h.absolutePos(new BlockPos(1,2,1));
        var destroy=new ArrayList<>(AmmoConfig.DESTROY_BLOCKS.get());
        var pass=new ArrayList<>(AmmoConfig.PASS_THROUGH_BLOCKS.get());
        boolean enabled=AmmoConfig.DESTROY_GLASS.get();double hardness=AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.get();
        var bullet=new EntityKineticBullet(EntityKineticBullet.TYPE,level);
        try {
            // No manual initialization before this: the actual common config loading hook must bind it.
            h.assertTrue(AmmoBlockConfigRead.canDestroy(Blocks.GLASS.defaultBlockState()),"Common config never initialized block policy");
            AmmoConfig.DESTROY_BLOCKS.set(List.of("minecraft:stone","minecraft:bedrock"));
            AmmoConfig.PASS_THROUGH_BLOCKS.set(List.of("#minecraft:planks"));
            AmmoConfig.DESTROY_GLASS.set(true);AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.set(1.0);
            AmmoBlockConfigRead.init();
            level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());hit(level,pos,bullet);
            h.assertTrue(level.getBlockState(pos).is(Blocks.STONE),"Hardness threshold ignored");
            AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.set(2.0);hit(level,pos,bullet);
            h.assertTrue(level.getBlockState(pos).isAir(),"Configured stone was not destroyed");
            level.setBlockAndUpdate(pos,Blocks.BEDROCK.defaultBlockState());hit(level,pos,bullet);
            h.assertTrue(level.getBlockState(pos).is(Blocks.BEDROCK),"Unbreakable block destroyed");
            level.setBlockAndUpdate(pos,Blocks.GLASS.defaultBlockState());hit(level,pos,bullet);
            h.assertTrue(level.getBlockState(pos).is(Blocks.GLASS),"Unlisted glass destroyed by legacy class heuristic");
            AmmoConfig.DESTROY_GLASS.set(false);level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());hit(level,pos,bullet);
            h.assertTrue(level.getBlockState(pos).is(Blocks.STONE),"Master destroy switch ignored");
            level.setBlockAndUpdate(pos,Blocks.OAK_PLANKS.defaultBlockState());
            var from=Vec3.atCenterOf(pos).add(-2,0,0);var to=from.add(4,0,0);
            var context=new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bullet);
            h.assertTrue(BlockRayTrace.rayTraceBlocks(level,context).getType()==HitResult.Type.MISS,"Configured block tag not pass-through");
            AmmoConfig.PASS_THROUGH_BLOCKS.set(List.of());AmmoBlockConfigRead.init();
            h.assertTrue(BlockRayTrace.rayTraceBlocks(level,context).getBlockPos().equals(pos),"Reload did not restore collision");
            System.out.println("PORT_AMMO_POLICY: loading hook, ID destroy list, hardness/unbreakable/master veto and tag ray pass-through/reload passed");
        } finally {
            AmmoConfig.DESTROY_BLOCKS.set(destroy);AmmoConfig.PASS_THROUGH_BLOCKS.set(pass);
            AmmoConfig.DESTROY_GLASS.set(enabled);AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.set(hardness);AmmoBlockConfigRead.init();
            level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());bullet.discard();
        }
        h.succeed();
    }
    private static void hit(net.minecraft.server.level.ServerLevel level,BlockPos pos,EntityKineticBullet bullet) {
        DestroyGlassBlock.onAmmoHitBlock(new AmmoHitBlockEvent(level,new BlockHitResult(Vec3.atCenterOf(pos),Direction.WEST,pos,false),level.getBlockState(pos),bullet));
    }
    public static void entityPolicies(GameTestHelper h) {
        var level=h.getLevel();var origin=Vec3.atCenterOf(h.absolutePos(new BlockPos(1,3,1)));
        var owner=h.spawn(EntityType.ZOMBIE,BlockPos.ZERO);
        var ally=h.spawn(EntityType.COW,new BlockPos(1,3,1));var enemy=h.spawn(EntityType.PIG,new BlockPos(1,3,2));
        ally.setPos(origin);enemy.setPos(origin.add(0,0,1));ally.setNoAi(true);enemy.setNoAi(true);
        var bullet=new EntityKineticBullet(EntityKineticBullet.TYPE,level);bullet.setOwner(owner);
        var from=origin.add(0,0,-2);var to=origin.add(0,0,3);bullet.setPos(from);bullet.setDeltaMovement(to.subtract(from));
        var list=new ArrayList<>(AmmoConfig.BULLET_DAMAGE_IGNORE_LIST.get());
        boolean enabled=AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.get(),friendly=AmmoConfig.IGNORE_TEAM_FRIENDLY_FIRE.get();
        var board=level.getScoreboard();var team=board.addPlayerTeam("ammo_"+ally.getId());team.setAllowFriendlyFire(false);
        board.addPlayerToTeam(owner.getScoreboardName(),team);board.addPlayerToTeam(ally.getScoreboardName(),team);
        try {
            AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.set(false);AmmoConfig.IGNORE_TEAM_FRIENDLY_FIRE.set(false);
            h.assertTrue(EntityUtil.findEntityOnPath(bullet,from,to).getEntity()==ally,"Control did not hit nearer ally");
            AmmoConfig.IGNORE_TEAM_FRIENDLY_FIRE.set(true);
            h.assertTrue(EntityUtil.findEntityOnPath(bullet,from,to).getEntity()==enemy,"Ignored ally shielded enemy behind it");
            team.setAllowFriendlyFire(true);
            h.assertTrue(EntityUtil.findEntityOnPath(bullet,from,to).getEntity()==ally,"Friendly-fire team opt-in ignored");
            AmmoConfig.IGNORE_TEAM_FRIENDLY_FIRE.set(false);AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.set(true);
            AmmoConfig.BULLET_DAMAGE_IGNORE_LIST.set(List.of("minecraft:cow"));
            h.assertTrue(EntityUtil.findEntityOnPath(bullet,from,to).getEntity()==enemy,"Ignore list did not affect nearest-hit selection");
            h.assertTrue(EntityUtil.findEntitiesOnPath(bullet,from,to).stream().noneMatch(r->r.getEntity()==ally),"Piercing hit list retained ignored entity");
            float cowHealth=ally.getHealth(),pigHealth=enemy.getHealth();
            new ProjectileExplosion(level,owner,bullet,null,null,origin.x,origin.y,origin.z,4,3,false,Explosion.BlockInteraction.KEEP).explode();
            h.assertTrue(ally.getHealth()==cowHealth && enemy.getHealth()<pigHealth,"Explosion did not preserve ignored entity while damaging control");
            System.out.println("PORT_AMMO_POLICY: actual nearest/piercing selection, team-friendly-fire opt-in, entity ignore and explosion control passed");
        } finally {
            AmmoConfig.BULLET_DAMAGE_IGNORE_LIST.set(list);AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.set(enabled);AmmoConfig.IGNORE_TEAM_FRIENDLY_FIRE.set(friendly);
            board.removePlayerTeam(team);owner.discard();ally.discard();enemy.discard();bullet.discard();
        }
        h.succeed();
    }
}
