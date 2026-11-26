package com.tacz.probe;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.ammo.AmmoSource;
import com.tacz.guns.api.item.ammo.AmmoSourceRegistry;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.entity.shooter.LivingEntityReload;
import com.tacz.guns.util.time.GunTime;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.List;

/** Exercises the shipped logic tables through the actual Java/LuaJ host, not a copy of their state machines. */
public final class DefaultReloadSmoke {
    private static Player owner;
    private static int available, withdrawals;
    static {
        AmmoSource reserve = new AmmoSource() {
            // Availability is advisory. A mod inventory can return less, including zero, at settlement.
            @Override public boolean hasAmmo(LivingEntity who, ItemStack stack) { return true; }
            @Override public int consumeAmmo(LivingEntity who, ItemStack stack, int requested) {
                int debit = Math.min(requested, available);
                available -= debit;
                withdrawals++;
                return debit;
            }
        };
        AmmoSourceRegistry.EVENT.register((entity, stack) -> entity == owner ? reserve : null);
    }

    public static void defaultScriptFeedsConserveReserve(GameTestHelper test) {
        var survival = test.makeMockPlayer(GameType.SURVIVAL);
        var creative = test.makeMockPlayer(GameType.CREATIVE);
        try {
            for (String id : List.of("m1866", "spas_12", "m1014", "kar98", "hk_mk23", "hcar")) {
                for (int reserve : new int[]{0, 2}) {
                    probe(test, survival, id, reserve, false, false);
                    probe(test, survival, id, reserve, true, false);
                }
                probe(test, survival, id, 2, false, true);
                probe(test, creative, id, 0, false, false);
            }
        } finally {
            owner = null;
        }
        GunMod.LOGGER.info("PORT_ACTION: six default Lua reloads conserved physical and dummy reserves and skipped creative debits");
        test.succeed();
    }

    private static void probe(GameTestHelper test, Player player, String id, int budget, boolean tactical, boolean dummy) {
        var builder = GunItemBuilder.create().setId(Identifier.fromNamespaceAndPath("tacz", id))
                .setAmmoCount(tactical ? 1 : 0).setAmmoInBarrel(tactical).setFireMode(FireMode.SEMI);
        if (id.equals("hcar")) builder.putAttachment(AttachmentType.EXTENDED_MAG, Identifier.parse("tacz:extended_mag_1"));
        var stack = builder.build();
        var gun = (AbstractGunItem) stack.getItem();
        if (dummy) gun.setDummyAmmoAmount(stack, budget);
        owner = player;
        available = budget;
        withdrawals = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var operator = IGunOperator.fromLivingEntity(player);
        operator.initialData();
        var data = operator.getDataHolder();
        data.drawTimestamp = -1;
        data.baseTimestamp = GunTime.nowMillis() - 60_000;
        operator.reload();
        String where = id + " tactical=" + tactical + " reserve=" + budget + " dummy=" + dummy + " creative=" + player.isCreative();
        test.assertTrue(data.reloadStateType.isReloading(), "Failed to start reload: " + where);
        var reload = new LivingEntityReload(player, data, null, null);
        // Cross both feed and finish boundaries, including several loop feed positions in one tick.
        // Monotonic elapsed times, no sleep or clock mutation. A repeated completed tick must be inert.
        for (int age = 10_000; age <= 30_000 && data.reloadStateType.isReloading(); age += 10_000) {
            data.reloadTimestamp = GunTime.nowMillis() - age;
            reload.tickReloadState();
        }
        reload.tickReloadState();
        test.assertTrue(!data.reloadStateType.isReloading() && !data.gunScriptFailed, "Did not finish: " + where);
        int magazine = gun.getCurrentAmmoCount(stack);
        int loaded = magazine + (gun.hasBulletInBarrel(stack) ? 1 : 0);
        int capacity = ScriptGuardSmoke.bind(player).getMaxAmmoCount();
        int expected = player.isCreative() ? capacity + (switch (id) {
            case "m1014", "spas_12", "kar98" -> 1; // these scripts fund a separate chamber before the magazine
            default -> 0;
        }) : (tactical ? 2 : 0) + budget;
        test.assertTrue(loaded == expected && magazine <= capacity,
                "Inventory mismatch: loaded=" + loaded + " expected=" + expected + ", " + where);
        test.assertTrue(dummy ? gun.getDummyAmmoAmount(stack) == 0 && withdrawals == 0
                        : player.isCreative() ? withdrawals == 0 : available == 0,
                "Wrong reserve charged: " + where);
    }
}
