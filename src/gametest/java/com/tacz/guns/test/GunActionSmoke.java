package com.tacz.guns.test;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.event.common.GunFinishReloadEvent;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.entity.shooter.LivingEntityAim;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;

import java.util.function.Consumer;

public class GunActionSmoke {
    // Fabric callbacks live for the process; never retain a previous test's world.
    private static Consumer<GunFinishReloadEvent> observe = event -> {};
    static { GunFinishReloadEvent.EVENT.register(event -> observe.accept(event)); }

    @GameTest
    public void reloadTransitionAndAimGate(GameTestHelper helper) {
        var entity = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
        var data = new ShooterDataHolder();
        var gun = GunItemBuilder.create().setId(Identifier.parse("port_test:oem")).build();
        data.currentGunItem = () -> gun;
        var aim = new LivingEntityAim(entity, data);
        data.reloadStateType = ReloadState.StateType.EMPTY_RELOAD_FINISHING;
        aim.aim(true);
        helper.assertTrue(!data.isAiming, "A new aim request bypassed force_aim_exit_on_reload");
        // Advance the actual no-script finishing tick without sleeping or consuming inventory again.
        data.reloadTimestamp = 0;
        int[] calls = {0};
        observe = event -> {
            if (event.getEntity() != entity) return;
            helper.assertTrue(event.getLogicalSide() == LogicalSide.SERVER && event.getGunItemStack() == gun,
                    "Wrong reload notification source");
            helper.assertTrue(data.reloadTimestamp == -1 && !data.reloadStateType.isReloading(), "Notification before settlement");
            calls[0]++;
        };
        try {
            var reload = new LivingEntityReload(entity, data, null, null);
            reload.tickReloadState();
            reload.tickReloadState();
            helper.assertTrue(calls[0] == 1, "Expected one finish event for a single reload");
            aim.aim(true);
            helper.assertTrue(data.isAiming, "Aim didn't unlock after reload");
        } finally {
            observe = event -> {};
        }
        helper.succeed();
    }
}
