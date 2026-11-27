package com.tacz.probe;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;
import com.tacz.guns.api.event.common.GunFinishReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScriptGuardSmoke {
    private static final Identifier PROBE = Identifier.parse("port_test:guard_probe");
    private static UUID watched;
    private static int finishes;
    static {
        GunFinishReloadEvent.EVENT.register(e -> {
            if (e.getLogicalSide().isServer() && e.getEntity() != null && e.getEntity().getUUID().equals(watched)) ++finishes;
        });
    }

    private static ItemStack stack(Identifier id) {
        return GunItemBuilder.create().setId(id).setFireMode(FireMode.SEMI).build();
    }

    static ModernKineticGunScriptAPI bind(LivingEntity shooter) {
        var api = new ModernKineticGunScriptAPI();
        api.setShooter(shooter);
        api.setItemStack(shooter.getMainHandItem());
        api.setDataHolder(IGunOperator.fromLivingEntity(shooter).getDataHolder());
        return api;
    }

    private static void arm(IGunOperator operator, String probe) {
        var data = operator.getDataHolder();
        data.scriptData = LuaValue.valueOf(probe);
        data.reloadTimestamp = 0;
        data.reloadStateType = ReloadState.StateType.EMPTY_RELOAD_FINISHING;
        data.isBolting = true;
        data.meleePrepTickCount = 4;
        data.shootTimestamp = 123;
    }

    private static void checkAborted(GameTestHelper helper, IGunOperator operator) {
        var data = operator.getDataHolder();
        helper.assertTrue(data.gunScriptFailed && data.scriptData == null && data.reloadTimestamp == -1
                && !data.reloadStateType.isReloading() && !operator.getSynReloadState().getStateType().isReloading()
                && !data.isBolting && !operator.getSynIsBolting() && data.meleePrepTickCount == -1
                && data.shootTimestamp == 123 && finishes == 0, "Failed script left a pending action or announced a reload");
    }

    public static void badReloadAndNestedFailureDoNotAnnounceSuccess(GameTestHelper helper) {
        var mob = helper.spawn(EntityType.COW, BlockPos.ZERO);
        mob.setNoAi(true);
        mob.setItemInHand(InteractionHand.MAIN_HAND, stack(PROBE));
        var operator = IGunOperator.fromLivingEntity(mob);
        operator.initialData();
        watched = mob.getUUID();
        finishes = 0;
        arm(operator, "bad ordinal");
        helper.runAfterDelay(3, () -> {
            checkAborted(helper, operator);
            var blocked = new AtomicInteger();
            schedule(bind(mob), blocked);
            helper.assertTrue(blocked.get() == 0, "Another callback ran on the failed draw");
            operator.draw(mob::getMainHandItem);
            arm(operator, "nested error");
            helper.runAfterDelay(3, () -> {
                checkAborted(helper, operator);
                operator.draw(mob::getMainHandItem);
                arm(operator, "finish normally");
                helper.runAfterDelay(3, () -> {
                    helper.assertTrue(!operator.getDataHolder().gunScriptFailed && finishes == 1,
                            "Redraw did not re-enable a normal scripted reload exactly once");
                    watched = null;
                    GunMod.LOGGER.info("PORT_ACTION: malformed and nested Lua errors isolated; fresh draw finished once");
                    helper.succeed();
                });
            });
        });
    }

    private static void schedule(ModernKineticGunScriptAPI api, AtomicInteger calls) {
        api.safeAsyncTask(new ZeroArgFunction() {
            @Override public LuaValue call() {
                calls.incrementAndGet();
                return TRUE;
            }
        }, 0, 1, 2);
    }

    public static void queuedCallbacksBelongToADrawAndAStack(GameTestHelper helper) {
        var mob = helper.spawn(EntityType.COW, BlockPos.ZERO);
        mob.setNoAi(true);
        var original = stack(PROBE);
        mob.setItemInHand(InteractionHand.MAIN_HAND, original);
        var operator = IGunOperator.fromLivingEntity(mob);
        operator.initialData();
        var oldDraw = new AtomicInteger();
        var oldStack = new AtomicInteger();
        var control = new AtomicInteger();
        schedule(bind(mob), oldDraw);
        operator.draw(mob::getMainHandItem); // same ID AND stack; only the draw generation changed
        schedule(bind(mob), oldStack);
        mob.setItemInHand(InteractionHand.MAIN_HAND, original.copy()); // dynamic supplier now yields a different stack
        schedule(bind(mob), control);
        helper.succeedWhen(() -> helper.assertTrue(control.get() == 2 && oldDraw.get() == 1 && oldStack.get() == 1
                && !operator.getDataHolder().gunScriptFailed, "Stale callback ran or cancelled the new draw"));
    }

    public static void unselectedMinigunCoolsWithoutTickingHeldGunTwice(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var parked = stack(Identifier.parse("tacz:minigun"));
        var gun = (IGun) parked.getItem();
        gun.setHeatAmount(parked, 4f);
        gun.setOverheatLocked(parked, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, parked.copy());
        player.getInventory().setItem(1, parked);
        var held = player.getMainHandItem();
        held.getItem().inventoryTick(held, helper.getLevel(), player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        parked.getItem().inventoryTick(parked, helper.getLevel(), player, null);
        helper.assertTrue(gun.getHeatAmount(held) == 4f && gun.getHeatAmount(parked) == 0.75f
                && gun.isOverheatLocked(parked), "Wrong slot or cooling amount");
        parked.getItem().inventoryTick(parked, helper.getLevel(), player, null);
        helper.assertTrue(gun.getHeatAmount(parked) == 0f && !gun.isOverheatLocked(parked), "Cooling past zero did not unlock");
        helper.succeed();
    }
}
