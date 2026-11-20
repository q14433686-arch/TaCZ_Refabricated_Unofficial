package com.tacz.guns.event;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunDisplayCaseSlotHelper;
import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.GunPegboardSlotHelper;
import com.tacz.guns.block.entity.DisplayRackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class DisplayRackInteractions {
    private DisplayRackInteractions() {}

    // Vanilla suppresses block use when sneaking with an item in the off hand. Handle that one lock chord.
    public static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.getMainHandItem().getItem() instanceof IGun
                || !(player.getOffhandItem().getItem() instanceof IGun) || player.isSpectator() || !player.isAlive()) return InteractionResult.PASS;
        var pos = hit.getBlockPos();
        if (!player.mayBuild() || !player.isWithinBlockInteractionRange(pos, 0) || !level.hasChunkAt(pos)) return InteractionResult.PASS;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof GunDisplayCaseBlock cabinet) || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        cabinet.toggleLockFromInteraction(level, pos, state, player);
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    public static InteractionResult attack(Player player, Level level, InteractionHand hand, BlockPos pos, Direction face) {
        if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isAlive() || player.getMainHandItem().is(Items.DEBUG_STICK)) return InteractionResult.PASS;
        if (!level.hasChunkAt(pos)) return InteractionResult.PASS;
        var state = level.getBlockState(pos);
        boolean cabinet = state.getBlock() instanceof GunDisplayCaseBlock;
        if (!(cabinet || state.getBlock() instanceof GunPegboardBlock) || cabinet && state.getValue(GunDisplayCaseBlock.LOCKED)) return InteractionResult.PASS;
        if (!player.mayBuild() || !level.mayInteract(player, pos) || !player.isWithinBlockInteractionRange(pos, 0)) return InteractionResult.PASS;
        // Attack packets don't carry a trustworthy hit location. Select the cell with the player's actual pick.
        if (!(player.pick(player.blockInteractionRange(), 1f, false) instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK || !pos.equals(hit.getBlockPos())) return InteractionResult.PASS;
        int slot = cabinet ? GunDisplayCaseSlotHelper.getSlot(state, pos, hit.getLocation()) : GunPegboardSlotHelper.getSlot(state, pos, hit.getLocation());
        if (!(level.getBlockEntity(pos) instanceof DisplayRackBlockEntity rack) || rack.getItem(slot).isEmpty()) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS; // send START_DESTROY_BLOCK; FAIL here would swallow it
        if (rack.removeItem(slot, !player.isCreative() || player.isShiftKeyDown())) {
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 1f, 1f);
        }
        return InteractionResult.CONSUME;
    }
}
