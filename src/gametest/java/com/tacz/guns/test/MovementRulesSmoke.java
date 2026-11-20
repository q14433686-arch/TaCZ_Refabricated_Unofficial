package com.tacz.guns.test;

import cn.sh1rocu.tacz.api.mixin.ForcePoseInjection;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.entity.sync.ModSyncedEntityData;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

final class MovementRulesSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
        ctx.waitFor(mc -> mc.player.onGround());
        world.getServer().runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            var operator = IGunOperator.fromLivingEntity(player);
            var pose = (ForcePoseInjection) player;
            var oldHand = player.getMainHandItem();
            boolean enabled = SyncConfig.ENABLE_CRAWL.get(), empty = SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.get();
            boolean airborne = SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.get();
            float aim = operator.getSynAimingProgress();
            boolean ground = player.onGround();
            try {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                SyncConfig.ENABLE_CRAWL.set(true);
                SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.set(true);
                // Server authority may still report airborne for a tick after client-side landing.
                player.setOnGround(true);
                operator.crawl(true);
                require(pose.tacz$getForcedPose() == Pose.SWIMMING, "Empty-hand crawl rejected");
                SyncConfig.ENABLE_CRAWL.set(false);
                operator.crawl(false);
                require(pose.tacz$getForcedPose() == null, "Disabled config blocked crawl exit");
                SyncConfig.ENABLE_CRAWL.set(true);
                SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.set(false);
                operator.crawl(true);
                require(pose.tacz$getForcedPose() == null, "Empty-hand restriction bypassed");
                pose.tacz$setForcedPose(Pose.CROUCHING);
                operator.crawl(false);
                require(pose.tacz$getForcedPose() == Pose.CROUCHING, "Inactive crawl erased another pose owner");
                pose.tacz$setForcedPose(null);
                player.setOnGround(false);
                ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(player, 1f);
                SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.set(false);
                require(InaccuracyType.getInaccuracyType(player) == InaccuracyType.AIM, "Default airborne ADS changed");
                SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.set(true);
                require(!InaccuracyType.getInaccuracyType(player).isAim(), "Airborne hip-fire option ignored");
                player.setOnGround(true);
                require(InaccuracyType.getInaccuracyType(player) == InaccuracyType.AIM, "Airborne option leaked onto ground");
            } finally {
                player.setOnGround(ground);
                operator.crawl(false);
                pose.tacz$setForcedPose(null);
                player.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
                ModSyncedEntityData.AIMING_PROGRESS_KEY.setValue(player, aim);
                SyncConfig.ENABLE_CRAWL.set(enabled);
                SyncConfig.ENABLE_CRAWL_WITHOUT_GUN.set(empty);
                SyncConfig.USE_HIP_FIRE_INACCURACY_WHILE_AIRBORNE.set(airborne);
            }
        });
        GunMod.LOGGER.info("PORT_MOVEMENT: empty-hand crawl, disabled exit, pose ownership and airborne ADS option passed");
    }
    private static void require(boolean result, String message) { if (!result) throw new AssertionError(message); }
}
