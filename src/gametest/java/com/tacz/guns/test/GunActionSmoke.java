package com.tacz.guns.test;

import cn.sh1rocu.tacz.api.LogicalSide;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.item.ammo.AmmoSource;
import com.tacz.guns.api.item.ammo.AmmoSourceRegistry;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.init.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
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

    @GameTest
    public void quickUnloadConservesRoundsAndHonorsScripts(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var id = Identifier.parse("port_test:scripted");
        var stack = GunItemBuilder.create().setId(id).setAmmoCount(3).setAmmoInBarrel(true).build();
        var gun = (AbstractGunItem) stack.getItem();
        var index = TimelessAPI.getCommonGunIndex(id).orElseThrow();
        helper.assertTrue(index.hasUnloadAmmoScript() && index.canUnloadAmmo(), "Missing server Lua capability");
        var boxStack = new ItemStack(ModItems.AMMO_BOX);
        var box = (IAmmoBox) boxStack.getItem();
        box.setAmmoId(boxStack, index.getGunData().getAmmoId());
        box.setAmmoCount(boxStack, 2);
        player.getInventory().setItem(1, boxStack);
        // The script unloads only one and leaves the chamber. No physical item should materialize twice.
        gun.unloadAmmo(new ShooterDataHolder(), stack, player);
        helper.assertTrue(gun.getCurrentAmmoCount(stack) == 2 && gun.hasBulletInBarrel(stack), "Script fell through to default");
        helper.assertTrue(box.getAmmoCount(boxStack) == 3, "Round didn't return to the matching box");
        gun.setDummyAmmoAmount(stack, 4);
        gun.setMaxDummyAmmoAmount(stack, 5);
        helper.assertTrue(gun.unloadAmmo(stack, player, true, -1) == 1 && gun.getDummyAmmoAmount(stack) == 5,
                "Capped dummy reserve lost or duplicated rounds");
        helper.assertTrue(gun.hasBulletInBarrel(stack) && gun.getCurrentAmmoCount(stack) == 1 && box.getAmmoCount(boxStack) == 3,
                "Unaccepted dummy rounds must stay loaded");
        helper.succeed();
    }

    private static LivingEntity customOwner;
    static {
        // No returnAmmo override: a third-party source from before this port does not accept refunds.
        AmmoSource legacy = new AmmoSource() {
            public boolean hasAmmo(LivingEntity entity, ItemStack gun) { return true; }
            public int consumeAmmo(LivingEntity entity, ItemStack gun, int requested) { return requested; }
        };
        AmmoSourceRegistry.EVENT.register((entity, gun) -> entity == customOwner ? legacy : null);
    }

    @GameTest
    public void noRefundIntoAnUnrelatedInventory(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var stack = GunItemBuilder.create().setId(Identifier.parse("port_test:scripted")).setAmmoCount(2).build();
        var gun = (AbstractGunItem) stack.getItem();
        customOwner = player;
        try {
            helper.assertTrue(gun.unloadAmmo(stack, player, false, 1) == 0 && gun.getCurrentAmmoCount(stack) == 2,
                    "Legacy custom provider turned into a source of physical item refunds");
        } finally { customOwner = null; }
        helper.succeed();
    }
}
