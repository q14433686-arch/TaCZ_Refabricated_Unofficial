package com.tacz.guns.test;

import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.init.ModBlocks;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.util.ItemNbtUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;

public class AmmoStorageSmoke {
    private static ItemStack ammo() {
        return AmmoItemBuilder.create().setId(Identifier.parse("tacz:12g")).build();
    }

    @GameTest
    public void transferTransactionsAndComponentIdentity(GameTestHelper helper) {
        var pos = BlockPos.ZERO;
        helper.setBlock(pos, ModBlocks.AMMO_STORAGE);
        var be = helper.getBlockEntity(pos, AmmoStorageBlockEntity.class);
        var storage = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(pos), Direction.DOWN);
        helper.assertTrue(storage != null, "Hoppers can't see the storage");
        var variant = ItemVariant.of(ammo());
        var namedStack = ammo();
        namedStack.set(DataComponents.CUSTOM_NAME, Component.literal("distinct"));
        var named = ItemVariant.of(namedStack);
        long initialRevision = be.getInventoryRevision();
        long capacity = be.getRemainingCapacity(variant.toStack());
        try (var tx = Transaction.openOuter()) {
            // A bulk request must not be narrowed to an int or become an empty ItemStack.
            helper.assertTrue(storage.insert(variant, Long.MAX_VALUE, tx) == capacity && capacity > 1, "Incorrect bulk insert limit");
        }
        helper.assertTrue(be.getUsedStacks() == 0 && be.getInventoryRevision() == initialRevision, "Aborted insert dirtied the BE");
        try (var tx = Transaction.openOuter()) {
            helper.assertTrue(storage.insert(variant, 3, tx) == 3 && storage.insert(named, 2, tx) == 2, "Lost stock while inserting");
            helper.assertTrue(storage.insert(ItemVariant.of(Items.STICK), 1, tx) == 0, "Accepted non-ammo");
            try (var nested = tx.openNested()) {
                helper.assertTrue(storage.extract(variant, 2, nested) == 2, "Nested extraction failed");
            }
            tx.commit();
        }
        helper.assertTrue(be.getInventoryRevision() == initialRevision + 1 && be.getContents().size() == 2, "Expected one commit and two variants");
        try (var tx = Transaction.openOuter()) {
            var plainView = storage.iterator().next();
            helper.assertTrue(plainView.getResource().equals(variant) && plainView.getAmount() == 3, "Rollback didn't restore exact stock");
            helper.assertTrue(plainView.extract(named, 1, tx) == 0 && plainView.getCapacity() >= 3, "View extracted a different variant");
        }
        helper.succeed();
    }

    @GameTest
    public void blockLootAndBothCraftingUpgradesRetainStock(GameTestHelper helper) {
        var pos = BlockPos.ZERO;
        var level = helper.getLevel();
        helper.setBlock(pos, ModBlocks.AMMO_STORAGE);
        var be = helper.getBlockEntity(pos, AmmoStorageBlockEntity.class);
        var sample = ammo();
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("saved variant"));
        helper.assertTrue(be.insertAmmo(sample.copyWithCount(73), false) == 73, "Initial stock rejected");
        var drops = Block.getDrops(be.getBlockState(), level, helper.absolutePos(pos), be);
        helper.assertTrue(drops.size() == 1 && drops.getFirst().is(ModItems.AMMO_STORAGE), "Block must drop once, not as loose stock");
        var stack = drops.getFirst();
        helper.assertTrue(!ItemNbtUtils.getTag(stack).getListOrEmpty("Entries").isEmpty(), "Loot lost BE Entries");
        // Look up both real recipes, not constructors: a missing tier discriminator is a regression.
        for (boolean diamond : new boolean[]{false, true}) {
            var grid = new ArrayList<>(Collections.nCopies(9, new ItemStack(diamond ? Items.DIAMOND : Items.GOLD_INGOT)));
            grid.set(4, stack);
            var input = CraftingInput.of(3, 3, grid);
            var recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level).orElseThrow();
            helper.assertTrue(!recipe.value().isSpecial() && recipe.value().display().getFirst() instanceof
                    net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay, "Missing vanilla 3x3 upgrade display");
            stack = recipe.value().assemble(input);
            helper.assertTrue(stack.getCount() == 1 && stack.is(diamond ? ModItems.AMMO_STORAGE_DIAMOND : ModItems.AMMO_STORAGE_GOLD), "Incorrect upgrade");
        }
        helper.setBlock(pos, ModBlocks.AMMO_STORAGE_DIAMOND);
        var replacement = helper.getBlockEntity(pos, AmmoStorageBlockEntity.class);
        ModBlocks.AMMO_STORAGE_DIAMOND.setPlacedBy(level, helper.absolutePos(pos), replacement.getBlockState(), null, stack);
        helper.assertTrue(replacement.getCapacityStacks() == 864 && replacement.extractAmmo(sample, 100, false).getCount() == 73,
                "Stock/variant not restored after drop, upgrade and placement");
        helper.assertTrue(replacement.getContents().isEmpty() && replacement.getComparatorSignal() == 0, "Duplicated stock after extraction");
        helper.succeed();
    }
}
