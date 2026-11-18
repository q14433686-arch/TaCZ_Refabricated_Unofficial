package com.tacz.guns.test;

import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.GunPegboardBlock;
import com.tacz.guns.block.PegboardShape;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.GunPegboardItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class DisplayRackSmoke {
    private static ItemStack sample() {
        var stack = AmmoItemBuilder.create().setId(Identifier.parse("tacz:12g")).build();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("display fixture"));
        return stack;
    }

    @GameTest
    public void connectedCaseLocksAndSurvivalContents(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(BlockPos.ZERO);
        var state = ModBlocks.GUN_DISPLAY_CASE.defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL);
        level.setBlock(pos.east(), state.setValue(GunDisplayCaseBlock.LEFT, true), Block.UPDATE_ALL);
        var cabinet = (GunDisplayCaseBlock) state.getBlock();
        var be = (GunDisplayCaseBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var held = sample().copyWithCount(2);
        var hand = InteractionHand.MAIN_HAND;
        player.setItemInHand(hand, held);
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.125, .125, .8125), Direction.NORTH, pos, false);
        level.getBlockState(pos).useItemOn(held, level, player, hand, hit);
        helper.assertTrue(be.getItem(0).isEmpty() && held.getCount() == 2, "Locked wall accepted a deposit");
        cabinet.toggleLockFromInteraction(level, pos, level.getBlockState(pos), player);
        helper.assertTrue(!level.getBlockState(pos.east()).getValue(GunDisplayCaseBlock.LOCKED), "Connected lock did not propagate");
        level.getBlockState(pos).useItemOn(held, level, player, hand, hit);
        level.getBlockState(pos).useItemOn(held, level, player, hand, hit);
        helper.assertTrue(be.getRotation(0) == 1 && held.getCount() == 1 && be.getItem(0).getCount() == 1, "Deposit/rotate charged twice");
        helper.assertTrue(!be.setItem(1, new ItemStack(Items.STICK)), "Accepted a non-displayable item");
        // Carry On 26.3 saves BE data, removes BE, then removes the block; normal breaking runs removal side effects.
        var saved = be.saveCustomOnly(level.registryAccess());
        level.removeBlockEntity(pos);
        level.removeBlock(pos, false);
        var area = new AABB(pos).inflate(2);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).stream().noneMatch(e -> ItemStack.isSameItemSameComponents(e.getItem(), held)), "Content dropped while being moved");
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var moved = (GunDisplayCaseBlockEntity) level.getBlockEntity(pos);
        moved.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        helper.assertTrue(moved.getRotation(0) == 1 && ItemStack.isSameItemSameComponents(moved.getItem(0), held), "Lost display components/rotation on restore");
        level.removeBlock(pos, false);
        helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(e -> ItemStack.isSameItemSameComponents(e.getItem(), held)).mapToInt(e -> e.getItem().getCount()).sum() == 1, "Normal removal did not drop exactly the displayed item");
        helper.succeed();
    }

    @GameTest
    public void structureFacingRotatesWithoutChangingOtherProperties(GameTestHelper helper) {
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
        for (var block : List.of(ModBlocks.TARGET, ModBlocks.STATUE, ModBlocks.WORKBENCH_211, ModBlocks.WORKBENCH_121, ModBlocks.WORKBENCH_111)) {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                for (var rot : net.minecraft.world.level.block.Rotation.values()) {
                    helper.assertTrue(state.rotate(rot) == state.setValue(facing, rot.rotate(state.getValue(facing))), "Incorrect rotated part: " + state);
                }
            }
        }
        helper.succeed();
    }

    @GameTest
    public void dyeLootAndCornerMirror(GameTestHelper helper) {
        var level = helper.getLevel();
        for (var item : List.of(ModItems.GUN_PEGBOARD, ModItems.GLOW_GUN_PEGBOARD)) {
            var input = CraftingInput.of(2, 1, List.of(new ItemStack(item), new ItemStack(Items.DYE.blue())));
            var recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            var dyed = recipe.value().assemble(input);
            helper.assertTrue(dyed.is(item) && dyed.getCount() == 1 && GunPegboardItem.hasCustomColor(dyed), "Native dye recipe returned the wrong board");
        }
        helper.setBlock(BlockPos.ZERO, ModBlocks.GUN_PEGBOARD);
        var rack = helper.getBlockEntity(BlockPos.ZERO, GunPegboardBlockEntity.class);
        rack.setColor(0x123456, false);
        var dropped = Block.getDrops(rack.getBlockState(), level, rack.getBlockPos(), rack);
        helper.assertTrue(dropped.size() == 1 && GunPegboardItem.getColor(dropped.getFirst()) == 0x123456, "Lost directly applied color in loot");
        rack.setColor(0x123456, true);
        helper.assertTrue(!GunPegboardItem.hasCustomColor(Block.getDrops(rack.getBlockState(), level, rack.getBlockPos(), rack).getFirst()), "Inherited dye duplicated in loot");
        var left = ModBlocks.GUN_PEGBOARD.defaultBlockState().setValue(GunPegboardBlock.SHAPE, PegboardShape.CORNER_LEFT);
        var original = new GunPegboardBlockEntity(BlockPos.ZERO, left);
        helper.assertTrue(!original.setItem(16, sample()) && original.setItem(17, sample()) && original.rotateItem(17), "Folded face active columns incorrect");
        var mirrored = new GunPegboardBlockEntity(BlockPos.ZERO, left.setValue(GunPegboardBlock.SHAPE, PegboardShape.CORNER_RIGHT));
        mirrored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), original.saveCustomOnly(level.registryAccess())));
        helper.assertTrue(ItemStack.isSameItemSameComponents(mirrored.getItem(18), sample()) && mirrored.getRotation(18) == 7, "Structure mirror lost slot or angle");
        helper.assertTrue(!original.getUpdateTag(level.registryAccess()).contains("SlotShape"), "Client packet must not mirror twice");
        helper.succeed();
    }
}
