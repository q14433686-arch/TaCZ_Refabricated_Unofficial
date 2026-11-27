package com.tacz.probe;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.network.message.*;
import com.tacz.guns.util.ItemNbtUtils;
import com.tacz.guns.util.SlotAdapterHelper;
import com.tacz.guns.util.VariantHelper;
import com.tacz.guns.util.VirtualOemAttachment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/** No creative inventory, operator commands, server-side test classes, or local slot writes.
 * Only the expected component snapshots survive the logout. The next join uses a fresh server JVM. */
final class PersistentInventoryProbe {
    private static final BlockPos STORE = new BlockPos(8, 201, 7), CASE = new BlockPos(8, 201, 9);
    private static final Identifier GUN = Identifier.parse("port_test:oem"), OEM = Identifier.parse("tacz:scope_acog_ta31"),
            PART = Identifier.parse("tacz:sight_deltapoint_rifle"), MOUNT = Identifier.parse("port_test:mount"), OPTIC = Identifier.parse("port_test:optic");
    private static final AttachmentType SCOPE = AttachmentType.SCOPE;
    private ItemStack lot, partWithVariant, displayed;
    private net.minecraft.nbt.CompoundTag expectedLot, expectedDisplay;

    void deposit(ClientGameTestContext ctx) {
        ctx.waitFor(mc -> find(mc, PersistentInventoryProbe::fixtureGun) >= 0 && find(mc, PersistentInventoryProbe::fixturePart) >= 0
                && count(mc, PersistentInventoryProbe::fixtureLot) == 7 && mc.level.getBlockEntity(CASE) instanceof GunDisplayCaseBlockEntity, 1200);
        ctx.runOnClient(mc -> {
            require(!mc.player.hasInfiniteMaterials(), "Survival player required");
        });
        DedicatedCrafting.open(ctx, STORE, AmmoStorageMenu.class);
        // The menu's bulk snapshot can arrive before vanilla's authoritative player slots, and
        // AmmoItem.inventoryTick may have just set MAX_STACK_SIZE on the console-supplied stack.
        ctx.waitFor(mc -> store(mc).getCapacityStacks() == 216 && store(mc).getEntries().isEmpty()
                && store(mc).slots.stream().anyMatch(s -> fixtureLot(s.getItem()) && s.getItem().getCount() == 7));
        ctx.runOnClient(mc -> {
            var menu = store(mc);
            var slot = menu.slots.stream().filter(s -> fixtureLot(s.getItem())).findFirst().orElseThrow();
            lot = slot.getItem().copyWithCount(1);
            require(lot.has(DataComponents.CUSTOM_NAME) && lot.isEnchanted(), "Storage input lost name/enchantment");
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slot.index, 0, ClickType.QUICK_MOVE, mc.player);
        });
        ctx.waitFor(mc -> stored(mc, 7) && count(mc, PersistentInventoryProbe::fixtureLot) == 0);
        ctx.runOnClient(mc -> clickEntry(mc, AmmoStorageMenu.Action.PICKUP_HALF));
        ctx.waitFor(mc -> stored(mc, 3) && same(lot, store(mc).getCarried()) && store(mc).getCarried().getCount() == 4);
        close(ctx);
        ctx.waitFor(mc -> count(mc, s -> same(lot, s)) == 4);

        int slot = ctx.computeOnClient(mc -> find(mc, PersistentInventoryProbe::fixtureGun));
        require(slot >= 0 && slot < 9, "Fixture gun not in hotbar");
        // If /give filled the empty selected slot, InventoryEvent may already have drawn it.
        // Choose a different slot first. A stale ready flag from the PREVIOUS draw is not an ack.
        int other = (slot + 1) % 9;
        ctx.runOnClient(mc -> select(mc, other));
        ctx.waitTicks(2);
        long previousDraw = ctx.computeOnClient(mc -> local(mc).clientDrawTimestamp);
        ctx.runOnClient(mc -> select(mc, slot));
        try {
            ctx.waitFor(mc -> local(mc).clientDrawTimestamp > previousDraw && !local(mc).clientStateLock
                    && com.tacz.guns.util.time.GunTime.hasElapsed(local(mc).clientDrawTimestamp, 600)
                    && operator(mc).getSynDrawCoolDown() == 0, 1200);
        } catch (AssertionError e) { throw new AssertionError("Didn't finish drawing the selected fixture: " + ctx.computeOnClient(PersistentInventoryProbe::actionState), e); }
        var before = ctx.computeOnClient(PersistentInventoryProbe::actionState);
        ctx.runOnClient(mc -> {
            var gun = (IGun) held(mc).getItem();
            require(gun.getCurrentAmmoCount(held(mc)) == 3 && gun.hasBulletInBarrel(held(mc)) && count(mc, PersistentInventoryProbe::looseRefund) == 0,
                    "Wrong initial stack/reserve");
            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).unloadAmmo();
        });
        try {
            ctx.waitFor(mc -> ((IGun) held(mc).getItem()).getCurrentAmmoCount(held(mc)) == 0 && !((IGun) held(mc).getItem()).hasBulletInBarrel(held(mc))
                    && count(mc, PersistentInventoryProbe::looseRefund) == 4, 1200);
        } catch (AssertionError e) { throw new AssertionError("Unsettled unload: before=" + before + " after=" + ctx.computeOnClient(PersistentInventoryProbe::actionState), e); }
        ctx.runOnClient(mc -> {
            ClientPlayNetworking.send(new ClientMessagePlayerUnloadGun(slot, GUN)); // empty: no extra return
            ClientPlayNetworking.send(new ClientMessageInstallVirtualAttachment(slot, SCOPE, OEM));
        });
        ctx.waitFor(mc -> VirtualOemAttachment.isMarked(installed(held(mc))) && count(mc, PersistentInventoryProbe::loosePart) == 1);
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new ClientMessageUnloadAttachment(slot, SCOPE)));
        ctx.waitFor(mc -> installed(held(mc)).isEmpty() && count(mc, PersistentInventoryProbe::loosePart) == 1);
        ctx.runOnClient(mc -> {
            var physical = find(mc, PersistentInventoryProbe::fixturePart);
            require(physical >= 0, "OEM removal replaced the physical part");
            partWithVariant = inSlot(mc, physical).copyWithCount(1);
            require(partWithVariant.has(DataComponents.CUSTOM_NAME) && partWithVariant.isEnchanted(), "Missing physical part name/enchantment");
            VariantHelper.setVariant(partWithVariant, OPTIC); // comparison only
            ClientPlayNetworking.send(new ClientMessageRefitGun(physical, slot, SCOPE)); // must refuse without the mount
            ClientPlayNetworking.send(new ClientMessageSelectRefitOption(slot, SCOPE, ClientMessageSelectRefitOption.Kind.ADAPTER, MOUNT));
        });
        ctx.waitFor(mc -> MOUNT.equals(SlotAdapterHelper.getSlotAdapter(held(mc), SCOPE)));
        ctx.runOnClient(mc -> {
            require(installed(held(mc)).isEmpty() && count(mc, PersistentInventoryProbe::loosePart) == 1, "Refusal consumed the part");
            ClientPlayNetworking.send(new ClientMessageRefitGun(find(mc, PersistentInventoryProbe::fixturePart), slot, SCOPE));
        });
        ctx.waitFor(mc -> fixturePart(installed(held(mc))) && count(mc, PersistentInventoryProbe::loosePart) == 0);
        ctx.runOnClient(mc -> {
            // The invalid removal must not run before installing this variant.
            ClientPlayNetworking.send(new ClientMessageSelectRefitOption(slot, SCOPE, ClientMessageSelectRefitOption.Kind.ADAPTER, null));
            ClientPlayNetworking.send(new ClientMessageSelectRefitOption(slot, SCOPE, ClientMessageSelectRefitOption.Kind.VARIANT, OPTIC));
        });
        ctx.waitFor(mc -> same(installed(held(mc)), partWithVariant) && MOUNT.equals(SlotAdapterHelper.getSlotAdapter(held(mc), SCOPE)));
        ctx.runOnClient(mc -> {
            ClientPlayNetworking.send(new ClientMessageUnloadAttachment(slot, SCOPE));
            ClientPlayNetworking.send(new ClientMessageUnloadAttachment(slot, SCOPE));
        });
        ctx.waitFor(mc -> installed(held(mc)).isEmpty() && count(mc, s -> same(s, partWithVariant)) == 1);
        ctx.runOnClient(mc -> ClientPlayNetworking.send(new ClientMessageRefitGun(find(mc, PersistentInventoryProbe::fixturePart), slot, SCOPE)));
        ctx.waitFor(mc -> same(installed(held(mc)), partWithVariant) && count(mc, PersistentInventoryProbe::loosePart) == 0);
        ctx.runOnClient(mc -> { displayed = held(mc).copy(); useCase(mc); });
        ctx.waitFor(mc -> onDisplay(mc, 0) && count(mc, PersistentInventoryProbe::fixtureGun) == 0);
        ctx.runOnClient(PersistentInventoryProbe::useCase);
        ctx.waitFor(mc -> onDisplay(mc, 1));
        ctx.runOnClient(mc -> {
            // World-registry holders (e.g. Unbreaking) are not identical Java objects after
            // reconnect. Carry only codec data across the boundary, never the old connection's stacks.
            expectedLot = ItemNbtUtils.saveItemStack(lot);
            expectedDisplay = ItemNbtUtils.saveItemStack(displayed);
            lot = partWithVariant = displayed = null;
        });
        System.out.println("PORT_INVENTORY: unload returned 3+1 rounds once; survival OEM removal gave no item; physical part returned once with components; 4 held + 3 stored");
    }

    void recover(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> {
            lot = ItemNbtUtils.loadItemStack(expectedLot);
            displayed = ItemNbtUtils.loadItemStack(expectedDisplay);
            require(fixtureLot(lot) && lot.isEnchanted() && fixtureGun(displayed) && installed(displayed).isEnchanted(),
                    "Failed to resolve saved component expectations in rejoined registries");
        });
        ctx.waitFor(mc -> mc.screen == null && mc.getConnection().hasClientLoaded() && onDisplay(mc, 1), 1200);
        ctx.runOnClient(mc -> require(count(mc, PersistentInventoryProbe::fixtureGun) == 0 && count(mc, PersistentInventoryProbe::loosePart) == 0
                && count(mc, s -> same(s, lot)) == 4 && count(mc, PersistentInventoryProbe::looseRefund) == 4, "Player data and world disagree on rejoin"));
        DedicatedCrafting.open(ctx, STORE, AmmoStorageMenu.class);
        ctx.waitFor(mc -> stored(mc, 3));
        ctx.runOnClient(mc -> clickEntry(mc, AmmoStorageMenu.Action.QUICK_MOVE));
        ctx.waitFor(mc -> store(mc).getEntries().isEmpty() && count(mc, s -> same(s, lot)) == 7);
        close(ctx);
        System.out.println("PORT_PERSIST: fresh dedicated JVM, same saved world/player: rotated display retained the fitted gun, named physical variant and mount; 3+4 named enchanted rounds returned as 7");
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }
    private static com.tacz.guns.client.gameplay.LocalPlayerDataHolder local(Minecraft mc) {
        return com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder();
    }
    private static com.tacz.guns.api.entity.IGunOperator operator(Minecraft mc) {
        return com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(mc.player);
    }
    private static String actionState(Minecraft mc) {
        var data = local(mc); var op = operator(mc); var s = held(mc); var i = com.tacz.guns.api.TimelessAPI.getCommonGunIndex(GUN);
        return "slot=" + mc.player.getInventory().getSelectedSlot() + " gun=" + fixtureGun(s)
                + " mag/chamber=" + (s.getItem() instanceof IGun g ? g.getCurrentAmmoCount(s) + "/" + g.hasBulletInBarrel(s) : "-")
                + " loose=" + count(mc, PersistentInventoryProbe::looseRefund) + " send=" + ClientPlayNetworking.canSend(ClientMessagePlayerUnloadGun.TYPE)
                + " capability=" + i.map(v -> v.canUnloadAmmo()).orElse(false) + " lock/suppressed=" + data.clientStateLock + "/" + data.autoReloadSuppressed
                + " cd(draw,shoot,melee)=" + op.getSynDrawCoolDown() + "," + op.getSynShootCoolDown() + "," + op.getSynMeleeCoolDown()
                + " reload=" + op.getSynReloadState().getStateType() + " aim/sprint=" + data.clientAimingProgress + "/" + op.getSynSprintTime();
    }
    private static ItemStack inSlot(Minecraft mc, int i) { return mc.player.getInventory().getItem(i); }
    private static ItemStack held(Minecraft mc) { return mc.player.getMainHandItem(); }
    private static boolean fixtureGun(ItemStack s) { return s.getItem() instanceof IGun g && GUN.equals(g.getGunId(s)); }
    private static boolean looseRefund(ItemStack s) { return s.getItem() instanceof com.tacz.guns.api.item.IAmmo a
            && a.getAmmoId(s).equals(Identifier.parse("tacz:762x39")) && !fixtureLot(s); }
    private static boolean fixturePart(ItemStack s) { return s.getItem() instanceof IAttachment p && PART.equals(p.getAttachmentId(s)); }
    private static boolean loosePart(ItemStack s) { return s.getItem() instanceof IAttachment; }
    private static boolean fixtureLot(ItemStack s) { return ItemNbtUtils.getTag(s).getIntOr("ProbeLot", 0) == 91; }
    private static ItemStack installed(ItemStack stack) { return stack.getItem() instanceof IGun g ? g.getAttachment(stack, SCOPE) : ItemStack.EMPTY; }
    private static boolean same(ItemStack a, ItemStack b) { return ItemStack.isSameItemSameComponents(a, b); }
    private static int find(Minecraft mc, Predicate<ItemStack> wanted) {
        for (int i = 0; i < 36; ++i) if (wanted.test(inSlot(mc, i))) return i;
        return -1;
    }
    private static int count(Minecraft mc, Predicate<ItemStack> wanted) {
        int total = 0;
        for (var s : mc.player.getInventory().getNonEquipmentItems()) if (wanted.test(s)) total += s.getCount();
        return total;
    }
    private static AmmoStorageMenu store(Minecraft mc) { return (AmmoStorageMenu) mc.player.containerMenu; }
    private boolean stored(Minecraft mc, int n) {
        var entries = store(mc).getEntries();
        return entries.size() == 1 && entries.getFirst().count() == n && same(entries.getFirst().sample(), lot);
    }
    private static void clickEntry(Minecraft mc, AmmoStorageMenu.Action action) {
        var menu = store(mc);
        ClientPlayNetworking.send(new ClientMessageAmmoStorageClick(menu.containerId, menu.getEntries().getFirst().id(), action));
    }
    private static void close(ClientGameTestContext ctx) {
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitFor(mc -> mc.screen == null && mc.player.containerMenu == mc.player.inventoryMenu);
    }
    private boolean onDisplay(Minecraft mc, int rotation) {
        return mc.level.getBlockEntity(CASE) instanceof GunDisplayCaseBlockEntity rack && rack.getRotation(0) == rotation
                && ItemStack.matches(displayed, rack.getItem(0));
    }
    private static void useCase(Minecraft mc) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atLowerCornerOf(CASE).add(.125,.125,.8125), Direction.NORTH, CASE, false));
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
