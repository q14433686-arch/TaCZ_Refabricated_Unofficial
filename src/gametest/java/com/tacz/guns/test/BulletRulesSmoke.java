package com.tacz.guns.test;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.config.util.AmmoBlockConfigRead;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.event.ammo.DestroyGlassBlock;
import com.tacz.guns.init.ModEntities;
import com.tacz.guns.util.EntityUtil;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public class BulletRulesSmoke extends EntityUtil {
    @GameTest
    public void configuredBlockAndEntityRules(GameTestHelper h) {
        var level = h.getLevel();
        var bullet = new EntityKineticBullet(ModEntities.BULLET, level);
        var mob = h.spawn(EntityType.ZOMBIE, BlockPos.ZERO);
        boolean enabled = AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.get();
        boolean destroy = AmmoConfig.DESTROY_GLASS.get();
        var ignored = AmmoConfig.BULLET_DAMAGE_IGNORE_LIST.get();
        var blocks = AmmoConfig.DESTROY_BLOCKS.get();
        var pass = AmmoConfig.PASS_THROUGH_BLOCKS.get();
        double hardness = AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.get();
        try {
            AmmoConfig.BULLET_DAMAGE_IGNORE_LIST.set(List.of("minecraft:zombie"));
            AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.set(false);
            h.assertTrue(!isBulletDamageIgnored(mob), "Disabled ignore list still filtered entity");
            AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.set(true);
            h.assertTrue(isBulletDamageIgnored(mob), "Enabled entity list ignored");
            Vec3 start = mob.position().add(-2, .5, 0), end = mob.position().add(2, .5, 0);
            h.assertTrue(getHitResult(bullet, mob, start, end) == null, "Ignored mob still consumes a ray hit");
            AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.set(false);
            h.assertTrue(getHitResult(bullet, mob, start, end) != null, "Normal mob cannot be hit");
            AmmoConfig.DESTROY_GLASS.set(true);
            AmmoConfig.DESTROY_BLOCKS.set(List.of("minecraft:glass", "minecraft:stone", "minecraft:bedrock"));
            AmmoConfig.PASS_THROUGH_BLOCKS.set(List.of("minecraft:glass", "#minecraft:logs", "invalid::id"));
            AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.set(1.0);
            AmmoBlockConfigRead.init();
            for (String id : List.of("glass", "stone", "bedrock")) {
                BlockPos pos = h.absolutePos(new BlockPos(2, 2, 2));
                var state = BuiltInRegistries.BLOCK.getOptional(Identifier.parse("minecraft:" + id)).orElseThrow().defaultBlockState();
                level.setBlock(pos, state, 3);
                var event = new AmmoHitBlockEvent(level, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), state, bullet);
                DestroyGlassBlock.onAmmoHitBlock(event);
                h.assertTrue(level.getBlockState(pos).isAir() == id.equals("glass"), "Hardness gate failed for " + id);
            }
            var glass = BuiltInRegistries.BLOCK.getOptional(Identifier.parse("minecraft:glass")).orElseThrow().defaultBlockState();
            var log = BuiltInRegistries.BLOCK.getOptional(Identifier.parse("minecraft:oak_log")).orElseThrow().defaultBlockState();
            h.assertTrue(AmmoBlockConfigRead.canPassThrough(glass), "Block ID did not match");
            h.assertTrue(AmmoBlockConfigRead.canPassThrough(log), "Block tag did not match");
            AmmoConfig.DESTROY_BLOCKS.set(List.of());
            AmmoBlockConfigRead.init();
            h.assertTrue(!AmmoBlockConfigRead.canDestroy(glass), "Empty list retained stale block cache");
        } finally {
            AmmoConfig.ENABLE_BULLET_DAMAGE_IGNORE_LIST.set(enabled);
            AmmoConfig.BULLET_DAMAGE_IGNORE_LIST.set(ignored);
            AmmoConfig.DESTROY_GLASS.set(destroy);
            AmmoConfig.DESTROY_BLOCKS.set(blocks);
            AmmoConfig.PASS_THROUGH_BLOCKS.set(pass);
            AmmoConfig.DESTROY_BLOCK_HARDNESS_THRESHOLD.set(hardness);
            AmmoBlockConfigRead.init();
            mob.discard();
        }
        h.succeed();
    }
    @GameTest
    public void onlyMigrateExactLegacyGlassDefaults(GameTestHelper helper) {
        var field = com.tacz.guns.config.common.AmmoConfig.DESTROY_BLOCKS;
        var saved = field.get();
        try {
            var legacy = java.util.List.of("#forge:glass", "#forge:glass_panes");
            var fresh = com.tacz.guns.config.common.AmmoConfig.DEFAULT_DESTROY_BLOCKS;
            for (var input : java.util.List.of(legacy, fresh, java.util.List.of("minecraft:glass"), legacy.reversed())) {
                field.set(input);
                com.tacz.guns.config.util.AmmoBlockConfigRead.init();
                helper.assertTrue(field.get().equals(input.equals(legacy) ? fresh : input), "Unexpected migration of " + input);
            }
        } finally {
            field.set(saved); field.save(); com.tacz.guns.config.util.AmmoBlockConfigRead.init();
        }
        helper.succeed();
    }
}
