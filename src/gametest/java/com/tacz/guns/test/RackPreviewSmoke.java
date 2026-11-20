package com.tacz.guns.test;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.client.renderer.block.DisplayRackRenderer;
import com.tacz.guns.init.ModBlocks;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
    private static volatile BlockPos target;
    private static volatile String clientUse, serverUse;
    private static boolean listening;
    private static void observeUse() {
        if (listening) return;
        listening = true;
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((p,level,hand,hit)->{
            if (hit.getBlockPos().equals(target)) {
                String info = hand+" shift="+p.isShiftKeyDown()+" inRange="+p.isWithinBlockInteractionRange(target,0)+" mayInteract="+level.mayInteract(p,target);
                if (level.isClientSide()) clientUse=info; else serverUse=info;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, Identifier id) {
        var pos = ctx.computeOnClient(mc -> mc.player.blockPosition().south(2).above());
        var saved = world.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getMainHandItem().copy());
        boolean creative = ctx.computeOnClient(mc -> mc.player.isCreative());
        var gun = GunItemBuilder.create().setId(id).putAttachment(AttachmentType.SCOPE, Identifier.parse("tacz:sight_uh1")).build();
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
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
                var l = s.getPlayerList().getPlayers().getFirst().level();
                ((DisplayRackBlockEntity)l.getBlockEntity(pos)).clearContent();
                l.setBlock(pos, ModBlocks.GUN_DISPLAY_CASE.defaultBlockState(), Block.UPDATE_ALL);
            });
            ctx.waitFor(mc -> mc.level.getBlockState(pos).is(ModBlocks.GUN_DISPLAY_CASE));
            equip(world, gun.copy());
            ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun);
            requireSlot(ctx, pos, false, "locked cabinet");
            DisplayCaseHintSmoke.run(ctx, world, pos); // unlocks via the real crouch + Use interaction
            equip(world, new ItemStack(Items.STICK));
            ctx.waitFor(mc -> mc.player.getMainHandItem().is(Items.STICK) && !mc.level.getBlockState(pos).getValue(GunDisplayCaseBlock.LOCKED));
            requireSlot(ctx, pos, false, "ineligible held item");
            equip(world, ItemStack.EMPTY);
            ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty());
            checkCell(ctx, world, pos, gun, "case");

            // A perpendicular neighbour folds the board: from the original camera the NORTH plate
            // is now its *secondary* face (slots 16..31), not another straight-board control.
            var board = ModBlocks.GUN_PEGBOARD.defaultBlockState();
            var facing = com.tacz.guns.block.GunPegboardBlock.FACING;
            world.getServer().runOnServer(s -> {
                var level = s.getPlayerList().getPlayers().getFirst().level();
                ((DisplayRackBlockEntity)level.getBlockEntity(pos)).clearContent();
                if (!level.isEmptyBlock(pos.east())) throw new AssertionError("Corner needs an empty neighbour");
                level.setBlock(pos,board.setValue(facing,net.minecraft.core.Direction.EAST),Block.UPDATE_ALL);
                level.setBlock(pos.east(),board.setValue(facing,net.minecraft.core.Direction.NORTH),Block.UPDATE_ALL);
            });
            ctx.waitFor(mc -> com.tacz.guns.block.GunPegboardSlotHelper.hasCorner(mc.level.getBlockState(pos)));
            equip(world,ItemStack.EMPTY);ctx.waitFor(mc->mc.player.getMainHandItem().isEmpty());
            checkCell(ctx,world,pos,gun,"corner");
            world.getServer().runOnServer(s->((DisplayRackBlockEntity)s.getPlayerList().getPlayers().getFirst().level().getBlockEntity(pos)).clearContent());
            equip(world,ItemStack.EMPTY);ctx.waitFor(mc->mc.player.getMainHandItem().isEmpty());
            checkCell(ctx,world,pos,new ItemStack(Items.EMERALD),"vanilla_quad");
            world.getServer().runOnServer(s->((DisplayRackBlockEntity)s.getPlayerList().getPlayers().getFirst().level().getBlockEntity(pos)).clearContent());
            equip(world,ItemStack.EMPTY);ctx.waitFor(mc->mc.player.getMainHandItem().isEmpty());
            var mesh = GunItemBuilder.create().setId(id).build();
            ((IGun)mesh.getItem()).setGunDisplayId(mesh,Identifier.parse("port_test:rack_mesh"));
            // The player sees a normal hand model; only the rack LOD can emit cyan geometry.
            var lodDistance = com.tacz.guns.config.client.RenderConfig.GUN_LOD_RENDER_DISTANCE;
            int previous = ctx.computeOnClient(mc -> lodDistance.get());
            try {
                ctx.runOnClient(mc -> lodDistance.set(0));
                checkCell(ctx,world,pos,mesh,"mesh");
            } finally { ctx.runOnClient(mc -> lodDistance.set(previous)); }
        } finally {
            ctx.getInput().releaseShift();
            world.getServer().runOnServer(s -> {
                var p = s.getPlayerList().getPlayers().getFirst();
                if (p.level().getBlockEntity(pos) instanceof DisplayRackBlockEntity rack) rack.clearContent();
                if (p.level().getBlockState(pos).is(ModBlocks.GUN_PEGBOARD) && p.level().getBlockState(pos.east()).is(ModBlocks.GUN_PEGBOARD))
                    p.level().removeBlock(pos.east(),false);
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
        if (name.equals("mesh")) ctx.waitFor(mc -> {
            var rig = com.tacz.guns.api.TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).orElseThrow().getExternalModel(true);
            return rig != null && rig.getLeft() instanceof cn.sh1rocu.tacz.compat.meshloader.model.TaczPolyMeshGunModel poly && poly.hasPolyMesh();
        });
        if (name.equals("corner") && slot < DisplayRackBlockEntity.SLOTS_PER_FACE) throw new AssertionError("Wrong corner face: "+slot);
        ctx.waitTicks(10);
        var ghost = ctx.takeScreenshot("tacz_" + name + "_ghost");
        ctx.getInput().holdShift();
        ctx.waitFor(mc -> mc.player.isShiftKeyDown());
        requireSlot(ctx, pos, false, "crouching");
        ctx.getInput().releaseShift();
        ctx.waitTicks(10);
        if (requireSlot(ctx, pos, true, "standing") != slot) throw new AssertionError("Aim changed during preview");
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
            var rack = (DisplayRackBlockEntity)p.level().getBlockEntity(pos);
            var held = p.getMainHandItem();
            if (!rack.getItem(slot).isEmpty() || held.getCount() != 1 || !ItemStack.isSameItemSameComponents(item, held))
                throw new AssertionError("Rendering a preview mutated the rack or hand");
        });
        // The same bound Interact action shown above the display, not a direct BlockState/BE call.
        ctx.runOnClient(mc -> {
            observeUse(); clientUse=serverUse="not dispatched"; target=pos;
            if (!com.tacz.guns.util.InputExtraCheck.isInGame() || !com.tacz.guns.config.util.InteractKeyConfigRead.canInteractBlock(mc.level.getBlockState(pos)))
                throw new AssertionError("Rack input preconditions: inGame="+com.tacz.guns.util.InputExtraCheck.isInGame()+", allow="+com.tacz.guns.config.util.InteractKeyConfigRead.canInteractBlock(mc.level.getBlockState(pos)));
        });
        ClientProbe.waitForShift(ctx, world, false);
        if (item.getItem() instanceof IGun) ctx.getInput().pressKey(com.tacz.guns.client.input.InteractKey.INTERACT_KEY);
        else ctx.getInput().pressKey(o -> o.keyUse);
        try { ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty()); }
        catch (AssertionError ex) { throw new AssertionError(name+" use: client="+clientUse+", server="+serverUse+", screen="+ctx.computeOnClient(mc->String.valueOf(mc.screen)),ex); }
        finally { target=null; }
        world.getServer().runOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
            var rack = (DisplayRackBlockEntity)p.level().getBlockEntity(pos);
            if (!p.getMainHandItem().isEmpty() || rack.getItem(slot).getCount() != 1 || !ItemStack.isSameItemSameComponents(item, rack.getItem(slot)))
                throw new AssertionError("Deposit did not transfer exactly one item with its components");
        });
        ctx.waitFor(mc -> mc.player.getMainHandItem().isEmpty() && !((DisplayRackBlockEntity)mc.level.getBlockEntity(pos)).getItem(slot).isEmpty());
        ctx.waitTicks(10);
        var placed = ctx.takeScreenshot("tacz_" + name + "_placed");
        if (name.equals("case")) {
            int prompt = ctx.computeOnClient(mc -> mc.font.width(Component.translatable("gui.tacz.interact_key.text.desc",
                    org.apache.commons.lang3.StringUtils.capitalize(InteractKey.INTERACT_KEY.getTranslatedKeyMessage().getString()))));
            DisplayCaseHintSmoke.checkBand(ctx, ClientProbe.read(ghost), "valid gun slot", -25, prompt);
            DisplayCaseHintSmoke.checkBand(ctx, ClientProbe.read(placed), "unlocked gun interact", 32, 0);
        }
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

    static void equip(TestSingleplayerContext w, ItemStack stack) {
        w.getServer().runOnServer(s -> {
            var p = s.getPlayerList().getPlayers().getFirst();
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            p.inventoryMenu.broadcastChanges();
        });
    }
    private static int delta(int a, int b) {
        return Math.abs((a & 255) - (b & 255)) + Math.abs((a >> 8 & 255) - (b >> 8 & 255)) + Math.abs((a >> 16 & 255) - (b >> 16 & 255));
    }
    private static boolean outlinePixel(int color, boolean occupied) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        return r > 165 && r - b > 75 && (occupied ? g < 110 && r - g > 90 : g > 130 && g - b > 50);
    }
    private static void checkOccupiedOutline(java.awt.image.BufferedImage empty, java.awt.image.BufferedImage placed) {
        int cx = empty.getWidth() / 2, cy = empty.getHeight() / 2, expected = 0, covered = 0;
        // Same camera and slot. Keep the HUD text above the board out of the reference mask.
        for (int y = cy - 18; y < cy + 46; y++) for (int x = cx - 60; x < cx + 60; x++) {
            if (!outlinePixel(empty.getRGB(x,y), false)) continue;
            expected++;
            boolean found = false;
            for (int dy = -2; dy <= 2 && !found; dy++) for (int dx = -2; dx <= 2; dx++) {
                if (outlinePixel(placed.getRGB(x+dx,y+dy), true)) { found = true; break; }
            }
            if (found) covered++;
        }
        if (expected < 60 || covered < .8 * expected) throw new AssertionError("Occupied outline visible at only " + covered + "/" + expected + " empty edge pixels");
        com.tacz.guns.GunMod.LOGGER.info("PORT_OUTLINE: occupied outline covers {}/{} reference edge pixels", covered, expected);
    }
    private static void compare(Path empty, Path ghost, Path placed) {
        try {
            var a = ImageIO.read(empty.toFile()); var b = ImageIO.read(ghost.toFile()); var c = ImageIO.read(placed.toFile());
            if (placed.getFileName().toString().endsWith("tacz_rack_placed.png")) checkOccupiedOutline(a, c);
            boolean mesh = placed.getFileName().toString().endsWith("tacz_mesh_placed.png");
            if (mesh && (delta(a.getRGB(100,100),b.getRGB(100,100)) > 45 || delta(a.getRGB(100,100),c.getRGB(100,100)) > 45))
                throw new AssertionError("Mesh scene changed outside the aimed cell (sky control)");
            int between = 0, model = 0;
            // Mark only pixels in the cell that change towards, but not all the way to, the deposited model.
            for (int y = a.getHeight()/2-40; y < a.getHeight()/2+40; y++) for (int x = a.getWidth()/2-44; x < a.getWidth()/2+44; x++) {
                int base = a.getRGB(x,y), g = b.getRGB(x,y), solid = c.getRGB(x,y), whole = delta(base,solid);
                // The mesh-only fixture has cyan polys and no Bedrock cubes. Exclude the cell
                // outline, item tooltip, arms, etc. from its pass condition.
                if (mesh && Math.min(solid >> 8 & 255, solid & 255) - (solid >> 16 & 255) < 50) continue;
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
