package com.tacz.guns.test;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import com.tacz.guns.client.renderer.block.DisplayRackRenderer;
import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Render the same cell before, during and after a real survival block interaction. */
final class RackPreviewSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, Identifier id) {
        var pos = ctx.computeOnClient(mc -> mc.player.blockPosition().south(2).above());
        var saved = world.getServer().computeOnServer(s -> world.getConnection().getServerPlayer().getMainHandItem().copy());
        boolean creative = ctx.computeOnClient(mc -> mc.player.isCreative());
        var gun = GunItemBuilder.create().setId(id).putAttachment(AttachmentType.SCOPE, Identifier.parse("tacz:sight_uh1")).build();
        world.getServer().runOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            if (!p.level().isEmptyBlock(pos)) throw new AssertionError("Display fixture requires an empty block");
            p.setGameMode(GameType.SURVIVAL);
            p.level().setBlock(pos, ModBlocks.GLOW_GUN_PEGBOARD.defaultBlockState(), Block.UPDATE_ALL);
            ((com.tacz.guns.block.entity.GunPegboardBlockEntity)p.level().getBlockEntity(pos)).setColor(0x4088bb, false);
        });
        try {
            equip(world, ItemStack.EMPTY);
            ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty() && !mc.player.isCreative() && mc.level.getBlockEntity(pos) instanceof DisplayRackBlockEntity);
            ctx.getInput().lookAt(pos);
            ctx.waitFor(mc -> mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos));
            checkCell(ctx, world, pos, gun, "rack");

            world.getServer().runOnServer(s -> {
                var l = world.getConnection().getServerPlayer().level();
                ((DisplayRackBlockEntity)l.getBlockEntity(pos)).clearContent();
                l.setBlock(pos, ModBlocks.GUN_DISPLAY_CASE.defaultBlockState(), Block.UPDATE_ALL);
            });
            ctx.waitFor(mc -> mc.level.getBlockState(pos).is(ModBlocks.GUN_DISPLAY_CASE));
            equip(world, gun.copy());
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun);
            requireSlot(ctx, pos, false, "locked cabinet");
            equip(world, new ItemStack(Items.STICK));
            world.getServer().runOnServer(s -> {
                var l = world.getConnection().getServerPlayer().level();
                l.setBlock(pos, l.getBlockState(pos).setValue(GunDisplayCaseBlock.LOCKED, false), Block.UPDATE_ALL);
            });
            ctx.waitFor(mc -> mc.player.getMainHandItem().is(Items.STICK) && !mc.level.getBlockState(pos).getValue(GunDisplayCaseBlock.LOCKED));
            requireSlot(ctx, pos, false, "ineligible held item");
            equip(world, ItemStack.EMPTY);
            ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty());
            checkCell(ctx, world, pos, gun, "case");
        } finally {
            ctx.getInput().releaseShift();
            world.getServer().runOnServer(s -> {
                var p = world.getConnection().getServerPlayer();
                if (p.level().getBlockEntity(pos) instanceof DisplayRackBlockEntity rack) rack.clearContent();
                p.level().removeBlock(pos, false);
                if (creative) p.setGameMode(GameType.CREATIVE);
            });
            equip(world, saved);
        }
    }

    private static void checkCell(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos pos, ItemStack item, String name) {
        ctx.waitTicks(12);
        var empty = ctx.takeScreenshot("tacz_" + name + "_empty");
        equip(world, item.copy());
        ctx.waitFor(mc -> ItemStack.isSameItemSameComponents(item, mc.player.getMainHandItem()));
        int slot = requireSlot(ctx, pos, true, "aimed " + name);
        ctx.waitTicks(10);
        var ghost = ctx.takeScreenshot("tacz_" + name + "_ghost");
        ctx.getInput().holdShift();
        ctx.waitFor(mc -> mc.player.isShiftKeyDown());
        requireSlot(ctx, pos, false, "crouching");
        ctx.getInput().releaseShift();
        ctx.waitTicks(10);
        if (requireSlot(ctx, pos, true, "standing") != slot) throw new AssertionError("Aim changed during preview");
        var hit = ctx.computeOnClient(mc -> (BlockHitResult) mc.hitResult);
        world.getServer().runOnServer(s -> {
            var p = world.getConnection().getServerPlayer();
            var rack = (DisplayRackBlockEntity)p.level().getBlockEntity(pos);
            var held = p.getMainHandItem();
            if (!rack.getItem(slot).isEmpty() || held.getCount() != 1 || !ItemStack.isSameItemSameComponents(item, held))
                throw new AssertionError("Rendering a preview mutated the rack or hand");
            rack.getBlockState().useItemOn(held, p.level(), p, InteractionHand.MAIN_HAND, hit);
            if (!p.getMainHandItem().isEmpty() || rack.getItem(slot).getCount() != 1 || !ItemStack.isSameItemSameComponents(item, rack.getItem(slot)))
                throw new AssertionError("Deposit did not transfer exactly one item with its components");
            p.inventoryMenu.broadcastChanges();
        });
        ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty() && !((DisplayRackBlockEntity)mc.level.getBlockEntity(pos)).getItem(slot).isEmpty());
        ctx.waitTicks(10);
        var placed = ctx.takeScreenshot("tacz_" + name + "_placed");
        compare(empty, ghost, placed);
        equip(world, item.copy());
        ctx.waitFor(mc -> !mc.player.getMainHandItem().isEmpty());
        requireSlot(ctx, pos, false, "occupied cell");
    }

    private static int requireSlot(ClientGameTestContext ctx, BlockPos pos, boolean eligible, String scenario) {
        return ctx.computeOnClient(mc -> {
            int slot = DisplayRackRenderer.previewSlot((DisplayRackBlockEntity)mc.level.getBlockEntity(pos), mc.player, mc.hitResult);
            if ((slot >= 0) != eligible) throw new AssertionError(scenario + ": slot=" + slot + ", hit=" + mc.hitResult);
            return slot;
        });
    }

    private static void equip(TestSingleplayerContext w, ItemStack stack) {
        w.getServer().runOnServer(s -> {
            var p = w.getConnection().getServerPlayer();
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            p.inventoryMenu.broadcastChanges();
        });
    }
    private static int delta(int a, int b) {
        return Math.abs((a & 255) - (b & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a >> 16 & 255) - (b >> 16 & 255));
    }
    private static void compare(Path empty, Path ghost, Path placed) {
        try {
            var a = ImageIO.read(empty.toFile()); var b = ImageIO.read(ghost.toFile()); var c = ImageIO.read(placed.toFile());
            int between = 0, model = 0;
            // Mark only pixels in the cell that change towards, but not all the way to, the deposited model.
            for (int y = a.getHeight()/2-40; y < a.getHeight()/2+40; y++) for (int x = a.getWidth()/2-44; x < a.getWidth()/2+44; x++) {
                int base = a.getRGB(x,y), g = b.getRGB(x,y), solid = c.getRGB(x,y), whole = delta(base,solid);
                if (whole < 48) continue;
                model++;
                int d = delta(base,g), end = delta(g,solid);
                if (d > 8 && end > 5 && d < whole && end < whole && d + end <= whole + 20) between++;
            }
            if (model < 60 || between < 30) throw new AssertionError(ghost.getFileName() + ": " + between + " intermediate / " + model + " changed pixels");
            com.tacz.guns.GunMod.LOGGER.info("PORT_RACK_PREVIEW: {}: {} intermediate / {} changed pixels", ghost.getFileName(), between, model);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
