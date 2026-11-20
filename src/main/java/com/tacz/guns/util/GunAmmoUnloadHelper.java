package com.tacz.guns.util;

import cn.sh1rocu.tacz.util.itemhandler.IItemHandler;
import cn.sh1rocu.tacz.util.itemhandler.ItemHandlerHelper;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.ammo.AmmoSourceRegistry;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Server-only quick-unload settlement. Detaching an extended magazine retains its existing rules. */
public final class GunAmmoUnloadHelper {
    private GunAmmoUnloadHelper() {}

    public static int unloadAmmo(LivingEntity shooter, ItemStack gun, AbstractGunItem item, CommonGunIndex index,
                                 boolean chamber, int amount) {
        if (!(shooter.level() instanceof ServerLevel level) || amount < -1 || item.useInventoryAmmo(gun)) return 0;
        int current = Math.max(0, item.getCurrentAmmoCount(gun));
        int magazine = amount == -1 ? current : Math.min(amount, current);
        boolean chambered = chamber && index.getGunData().getBolt() != Bolt.OPEN_BOLT && item.hasBulletInBarrel(gun);
        long total = (long) magazine + (chambered ? 1 : 0);
        if (total <= 0 || total > Integer.MAX_VALUE) return 0;
        int accepted = (int) total;
        var reload = index.getGunData().getReloadData();
        boolean refund = IGunOperator.fromLivingEntity(shooter).needCheckAmmo() && !reload.isInfinite() && reload.getType() != FeedType.FUEL;
        if (refund && item.useDummyAmmo(gun)) {
            long limit = item.hasMaxDummyAmmo(gun) ? item.getMaxDummyAmmoAmount(gun) : Integer.MAX_VALUE;
            int reserve = item.getDummyAmmoAmount(gun);
            accepted = (int) Math.min(accepted, Math.max(0, limit - reserve));
            item.setDummyAmmoAmount(gun, reserve + accepted);
        } else if (refund) {
            // An external inventory may represent a non-item resource. Never convert its rejected refund into loose items.
            var custom = AmmoSourceRegistry.returnToCustomSource(shooter, gun, accepted);
            if (custom.isPresent()) {
                accepted = custom.getAsInt();
            } else {
                var id = index.getGunData().getAmmoId();
                var definition = TimelessAPI.getCommonAmmoIndex(id).orElse(null);
                if (definition == null) return 0;
                var ammo = AmmoItemBuilder.create().setId(id).build();
                int chunk = Math.min(ammo.getMaxStackSize(), definition.getStackSize());
                // Bound per-request entity/stack work even for malformed saved counts. Excess stays in the gun.
                if (ammo.isEmpty() || chunk < 1) return 0;
                accepted = (int) Math.min(accepted, (long) chunk * 256);
                IItemHandler inventory = shooter.tacz$getItemHandler(null).orElse(null);
                int rest = inventory == null ? accepted : fillMatchingBoxes(inventory, id, definition.getStackSize(), accepted);
                while (rest > 0) {
                    int count = Math.min(rest, chunk);
                    var stack = ammo.copyWithCount(count);
                    if (shooter instanceof Player player) ItemHandlerHelper.giveItemToPlayer(player, stack);
                    else {
                        var overflow = inventory == null ? stack : ItemHandlerHelper.insertItemStacked(inventory, stack, false);
                        if (!overflow.isEmpty()) shooter.spawnAtLocation(level, overflow);
                    }
                    rest -= count;
                }
            }
        }
        if (accepted > 0) {
            item.setCurrentAmmoCount(gun, current - Math.min(magazine, accepted));
            if (accepted > magazine && chambered) item.setBulletInBarrel(gun, false);
        }
        return accepted;
    }

    private static int fillMatchingBoxes(IItemHandler inventory, Identifier id, int stackSize, int remaining) {
        for (int i = 0; i < inventory.getSlots() && remaining > 0; i++) {
            var stack = inventory.getStackInSlot(i);
            if (!(stack.getItem() instanceof IAmmoBox box) || box.isCreative(stack) || box.isAllTypeCreative(stack)
                    || !id.equals(box.getAmmoId(stack))) continue;
            int count = box.getAmmoCount(stack);
            if (count < 0) continue;
            long base = Math.min(Integer.MAX_VALUE, (long) stackSize * SyncConfig.AMMO_BOX_STACK_SIZE.get());
            long capacity = Math.min(Integer.MAX_VALUE, base * (1L + box.getAmmoLevel(stack)));
            int fill = (int) Math.min(remaining, Math.max(0, capacity - count));
            if (fill > 0) box.setAmmoCount(stack, count + fill);
            remaining -= fill;
        }
        return remaining;
    }
}
