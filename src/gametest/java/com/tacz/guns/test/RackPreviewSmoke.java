package com.tacz.guns.test;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import com.tacz.guns.client.renderer.block.DisplayRackRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.*;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.Path;

final class RackPreviewSmoke {
    static void run(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos pos, Identifier id) {
        // The persistence test reopens this same save. Keep its fixtures intact even on failure.
        var cabinetPos = pos.east();
        record Saved(ItemStack board, ItemStack cabinet, net.minecraft.world.level.block.state.BlockState state) {}
        var saved = world.getServer().computeOnServer(s -> {var l=world.getConnection().getServerPlayer().level();
            return new Saved(((DisplayRackBlockEntity)l.getBlockEntity(pos)).getItem(6).copy(),
                    ((DisplayRackBlockEntity)l.getBlockEntity(cabinetPos)).getItem(9).copy(),l.getBlockState(cabinetPos));});
        try { check(ctx,world,pos,id); }
        finally {world.getServer().runOnServer(s->{var l=world.getConnection().getServerPlayer().level();
            var board=(DisplayRackBlockEntity)l.getBlockEntity(pos);board.clearContent();board.setItem(6,saved.board);board.rotateItem(6);
            l.setBlock(cabinetPos,saved.state,Block.UPDATE_ALL);var cabinet=(DisplayRackBlockEntity)l.getBlockEntity(cabinetPos);
            cabinet.clearContent();cabinet.setItem(9,saved.cabinet);}); equip(world,ItemStack.EMPTY);}
    }
    private static void check(ClientGameTestContext ctx, TestSingleplayerContext world, BlockPos pos, Identifier id) {
        // Remove the old example from this board so the centered cell and its neighbors are empty.
        world.getServer().runOnServer(s -> ((DisplayRackBlockEntity)world.getConnection().getServerPlayer().level().getBlockEntity(pos)).clearContent());
        ctx.waitFor(mc -> ((DisplayRackBlockEntity)mc.level.getBlockEntity(pos)).getItem(6).isEmpty());
        ctx.getInput().lookAt(pos); ctx.waitTicks(6);
        var control=ctx.takeScreenshot("tacz_rack_empty");
        equip(world,GunItemBuilder.create().setId(id).putAttachment(AttachmentType.SCOPE,Identifier.parse("tacz:sight_uh1")).build());
        ctx.waitFor(mc -> mc.player.getMainHandItem().getItem() instanceof IGun && mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(pos));
        int slot=ctx.computeOnClient(mc -> DisplayRackRenderer.previewSlot((DisplayRackBlockEntity)mc.level.getBlockEntity(pos),mc.player,mc.hitResult));
        if(slot<0)throw new AssertionError("Expected an aimed empty board cell");
        ctx.waitTicks(8); var ghost=ctx.takeScreenshot("tacz_rack_ghost");
        ctx.getInput().holdShift(); ctx.waitFor(mc -> DisplayRackRenderer.previewSlot((DisplayRackBlockEntity)mc.level.getBlockEntity(pos),mc.player,mc.hitResult)<0);
        ctx.getInput().releaseShift(); ctx.waitTicks(5);
        world.getServer().runOnServer(s -> { var p=world.getConnection().getServerPlayer(); var board=(DisplayRackBlockEntity)p.level().getBlockEntity(pos);
            if(!board.getItem(slot).isEmpty() || p.getMainHandItem().getCount()!=1 || !board.setItem(slot,p.getMainHandItem())) throw new AssertionError("Preview mutated rack or hand"); });
        ctx.waitFor(mc -> !((DisplayRackBlockEntity)mc.level.getBlockEntity(pos)).getItem(slot).isEmpty());
        ctx.runOnClient(mc -> { if(DisplayRackRenderer.previewSlot((DisplayRackBlockEntity)mc.level.getBlockEntity(pos),mc.player,mc.hitResult)>=0) throw new AssertionError("Ghost over occupied slot"); });
        ctx.waitTicks(6); var placed=ctx.takeScreenshot("tacz_rack_placed");
        compare(control,ghost,placed);
        var cabinet=pos.east(); ctx.getInput().lookAt(cabinet); ctx.waitTicks(5);
        ctx.runOnClient(mc -> {if(DisplayRackRenderer.previewSlot((DisplayRackBlockEntity)mc.level.getBlockEntity(cabinet),mc.player,mc.hitResult)>=0)throw new AssertionError("Locked case ghost");});
        world.getServer().runOnServer(s -> {var l=world.getConnection().getServerPlayer().level(); var be=(DisplayRackBlockEntity)l.getBlockEntity(cabinet); be.clearContent();
            l.setBlock(cabinet,l.getBlockState(cabinet).setValue(GunDisplayCaseBlock.LOCKED,false),Block.UPDATE_ALL);});
        ctx.waitFor(mc -> DisplayRackRenderer.previewSlot((DisplayRackBlockEntity)mc.level.getBlockEntity(cabinet),mc.player,mc.hitResult)>=0);
        PackClientSmoke.saveFrame(ctx,"tacz_case_ghost");
        equip(world,new ItemStack(Items.STICK));
        ctx.waitFor(mc -> mc.player.getMainHandItem().is(Items.STICK) && DisplayRackRenderer.previewSlot((DisplayRackBlockEntity)mc.level.getBlockEntity(cabinet),mc.player,mc.hitResult)<0);
        equip(world,ItemStack.EMPTY);
    }
    private static void equip(TestSingleplayerContext w,ItemStack stack) {w.getServer().runOnServer(s->{var p=w.getConnection().getServerPlayer();p.setItemInHand(InteractionHand.MAIN_HAND,stack);p.inventoryMenu.broadcastChanges();});}
    private static int delta(int a,int b){int total=0;for(int shift:new int[]{0,8,16})total+=Math.abs((a>>shift&255)-(b>>shift&255));return total;}
    private static void compare(Path empty,Path ghost,Path placed){
        try {var a=ImageIO.read(empty.toFile());var b=ImageIO.read(ghost.toFile());var c=ImageIO.read(placed.toFile());int between=0,model=0;
            // Inside the board, away from HUD/hand/crosshair. Count pixels where the ghost
            // changes the background towards (but not all the way to) the installed model.
            for(int y=a.getHeight()/2-40;y<a.getHeight()/2+40;y++)for(int x=a.getWidth()/2-44;x<a.getWidth()/2+44;x++){
                int base=a.getRGB(x,y), g=b.getRGB(x,y), solid=c.getRGB(x,y);int whole=delta(base,solid);
                if(whole<48)continue;model++;int d=delta(base,g),end=delta(g,solid);
                if(d>8 && end>5 && d<whole && end<whole && d+end<=whole+20)between++;
            }
            if(model<60 || between<30)throw new AssertionError("Missing translucent model in cell: "+between+" intermediate / "+model+" changed pixels");
            com.tacz.guns.GunMod.LOGGER.info("PORT_RACK_PREVIEW: {} intermediate / {} changed board pixels",between,model);
        } catch(IOException e){throw new UncheckedIOException(e);}
    }
}
