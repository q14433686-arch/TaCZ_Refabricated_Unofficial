package com.tacz.probe;

import com.tacz.guns.init.ModItems;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.message.ClientMessageCraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The test client is a non-op survival player. Use ordinary table interaction and slot clicks;
 * never construct a recipe, set a slot, grant a recipe, or rely on a viewer transfer button. */
final class DedicatedCrafting {
    private static final BlockPos TABLE = new BlockPos(9, 201, 8);

    static void craft(ClientGameTestContext ctx, int before) {
        ctx.waitFor(mc -> mc.level.getBlockState(TABLE).is(Blocks.CRAFTING_TABLE)
                && mc.getConnection().hasClientLoaded() && mc.screen == null && mc.player.onGround()
                && mc.player.position().distanceToSqr(Vec3.atCenterOf(TABLE)) < 5
                && inventory(mc, Items.IRON_INGOT) == 8 - 4 * before && recipeBookHasTable(mc), 1200);
        ctx.runOnClient(mc -> {
            if (mc.player.hasInfiniteMaterials() || inventory(mc, ModItems.GUN_SMITH_TABLE) != before)
                throw new AssertionError("Not starting a survival craft on the expected stock");
        });
        open(ctx, TABLE, CraftingMenu.class);
        ctx.runOnClient(mc -> {
            place(mc, Items.OAK_LOG, 1, 2, 3);
            place(mc, Items.IRON_INGOT, 4, 6, 7, 9);
            if (!mc.player.containerMenu.getSlot(0).getItem().isEmpty()) throw new AssertionError("Output without iron block");
            place(mc, Items.IRON_BLOCK, 5);
        });
        // CraftingMenu's result comes from the server, not the preview or client prediction.
        ctx.waitFor(mc -> mc.player.containerMenu.getSlot(0).getItem().is(ModItems.GUN_SMITH_TABLE));
        ctx.runOnClient(mc -> mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, mc.player));
        ctx.waitFor(mc -> inventory(mc, ModItems.GUN_SMITH_TABLE) == before + 1 && mc.player.containerMenu.getSlot(0).getItem().isEmpty());
        ctx.runOnClient(mc -> mc.player.closeContainer());
        ctx.waitFor(mc -> mc.player.containerMenu == mc.player.inventoryMenu);
    }
    private static void open(ClientGameTestContext ctx, BlockPos pos, Class<?> menu) {
        ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).relative(Direction.UP, .5), Direction.UP, pos, false)));
        try { ctx.waitFor(mc -> menu.isInstance(mc.player.containerMenu), 400); }
        catch (AssertionError e) { throw new AssertionError(ctx.computeOnClient(mc -> "No " + menu.getSimpleName()
                + " from " + mc.level.getBlockState(pos) + ", pos=" + mc.player.position() + ", screen=" + mc.screen
                + ", loaded=" + mc.getConnection().hasClientLoaded() + ", hand=" + mc.player.getMainHandItem()), e); }
    }

    static int shells(Minecraft mc) {
        var inv = mc.player.getInventory();
        int count = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (s.getItem() instanceof IAmmo ammo && ammo.getAmmoId(s).equals(Identifier.parse("tacz:12g"))) count += s.getCount();
        }
        return count;
    }

    static void table(ClientGameTestContext ctx, Identifier recipe, int before) {
        open(ctx, TABLE.offset(-2, 0, 0), GunSmithTableMenu.class);
        ctx.runOnClient(mc -> {
            var menu = (GunSmithTableMenu) mc.player.containerMenu;
            if (!Identifier.parse("tacz:ammo_workbench").equals(menu.getBlockId()) || shells(mc) != 5 * before
                    || !menu.selectFromViewer(recipe, false) || menu.consumeRecipeSelection() != null)
                throw new AssertionError("Unfunded selection or wrong workbench: " + menu.getBlockId());
            ClientPlayNetworking.send(new ClientMessageCraft(recipe, menu.containerId));
            // Generic inventory sync resumes when the slotless pack screen closes. TCP ordering
            // delivers the craft before the close (the server also checks the live container ID).
            mc.player.closeContainer();
        });
        ctx.waitFor(mc -> shells(mc) == 5 * (before + 1) && inventory(mc, Items.STICK) == 1 - before);
        ctx.waitFor(mc -> mc.screen == null);
    }

    static boolean recipeBookHasTable(Minecraft mc) {
        var context = SlotDisplayContext.fromLevel(mc.level);
        return mc.player.getRecipeBook().getCollections().stream().flatMap(c -> c.getRecipes().stream())
                .flatMap(e -> e.display().result().resolveForStacks(context).stream()).anyMatch(s -> s.is(ModItems.GUN_SMITH_TABLE));
    }
    static int inventory(Minecraft mc, Item item) { return mc.player.getInventory().countItem(item); }

    private static void place(Minecraft mc, Item item, int... grid) {
        var menu = mc.player.containerMenu;
        if (!menu.getCarried().isEmpty()) throw new AssertionError("Occupied cursor");
        for (int slot = 10; slot < menu.slots.size(); slot++) {
            if (!menu.getSlot(slot).getItem().is(item)) continue;
            mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
            for (int cell : grid) mc.gameMode.handleContainerInput(menu.containerId, cell, 1, ContainerInput.PICKUP, mc.player);
            if (!menu.getCarried().isEmpty()) mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
            return;
        }
        throw new AssertionError("Missing ingredient stock: " + item);
    }
    private DedicatedCrafting() {}
}
