package com.tacz.probe;

import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.block.GunDisplayCaseBlock;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.init.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.*;

public class RackChecks {
    private static ItemStack sample() {
        var stack = AmmoItemBuilder.create().setId(Identifier.parse("tacz:12g")).build();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("display fixture"));
        return stack;
    }

    public static void connectedCaseLocksAndSurvivalContents(GameTestHelper helper) {
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
        CatalogChecks.require(be.getItem(0).isEmpty() && held.getCount() == 2, "Locked wall accepted a deposit");
        cabinet.toggleLockFromInteraction(level, pos, level.getBlockState(pos), player);
        CatalogChecks.require(!level.getBlockState(pos.east()).getValue(GunDisplayCaseBlock.LOCKED), "Connected lock did not propagate");
        level.getBlockState(pos).useItemOn(held, level, player, hand, hit);
        level.getBlockState(pos).useItemOn(held, level, player, hand, hit);
        CatalogChecks.require(be.getRotation(0) == 1 && held.getCount() == 1 && be.getItem(0).getCount() == 1, "Deposit/rotate charged twice");
        CatalogChecks.require(!be.setItem(1, new ItemStack(Items.STICK)), "Accepted a non-displayable item");
        // A mover saves BE data, removes BE, then removes the block; normal breaking runs removal side effects.
        var saved = be.saveWithoutMetadata(level.registryAccess());
        level.removeBlockEntity(pos);
        level.removeBlock(pos, false);
        var area = new AABB(pos).inflate(2);
        CatalogChecks.require(level.getEntitiesOfClass(ItemEntity.class, area).stream().noneMatch(e -> ItemStack.isSameItemSameComponents(e.getItem(), held)), "Content dropped while being moved");
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var moved = (GunDisplayCaseBlockEntity) level.getBlockEntity(pos);
        moved.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        CatalogChecks.require(moved.getRotation(0) == 1 && ItemStack.isSameItemSameComponents(moved.getItem(0), held), "Lost display components/rotation on restore");
        level.removeBlock(pos, false);
        CatalogChecks.require(level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(e -> ItemStack.isSameItemSameComponents(e.getItem(), held)).mapToInt(e -> e.getItem().getCount()).sum() == 1, "Normal removal did not drop exactly the displayed item");
        statueAndRotation(helper);
        helper.succeed();
    }

    private static void statueAndRotation(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(5, 0, 0));
        for (var block : new Block[]{ModBlocks.STATUE, ModBlocks.TARGET, ModBlocks.GUN_SMITH_TABLE}) {
            var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
            var state = block.defaultBlockState().setValue(facing, Direction.NORTH);
            CatalogChecks.require(state.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).getValue(facing) == Direction.EAST,
                    "Native structure rotation did not rotate " + block);
        }
        level.setBlock(pos, ModBlocks.STATUE.defaultBlockState(), Block.UPDATE_ALL);
        var be = (com.tacz.guns.block.entity.StatueBlockEntity)level.getBlockEntity(pos);
        var original = sample().copyWithCount(7);
        be.setGun(original);
        CatalogChecks.require(original.getCount() == 7 && be.getGunItem().getCount() == 1,
                "Statue copied a stack instead of one physical display item or mutated the caller");
        var saved = be.saveWithoutMetadata(level.registryAccess());
        CatalogChecks.require(saved.equals(be.getUpdateTag(level.registryAccess())), "Statue update packet lost registry-aware data");
        var restored = new com.tacz.guns.block.entity.StatueBlockEntity(pos, be.getBlockState());
        restored.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        CatalogChecks.require(restored.getGunItem().getCount() == 1 && ItemStack.isSameItemSameComponents(original, restored.getGunItem()),
                "Statue disk/network round trip lost named components");
        be.setGun(sample()); // exactly one replaced item, then exactly one explicit removal
        be.dropItem();
        be.dropItem();
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).stream()
                .filter(e -> ItemStack.isSameItemSameComponents(original, e.getItem())).mapToInt(e -> e.getItem().getCount()).sum();
        CatalogChecks.require(drops == 2 && be.getGunItem().isEmpty(), "Statue replacement/removal duplicated or lost contents: " + drops);
        System.out.println("PORT_STATUE: one-count copy, component disk/update tag roundtrip, replacement + repeated drop, three native facing rotations passed");
    }

}
