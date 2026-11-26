package com.tacz.guns.crafting;

import com.mojang.serialization.Codec;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AmmoStorageUpgradeRecipe implements CraftingRecipe {
    private final boolean diamond;
    private final ShapedRecipePattern pattern;
    private final PlacementInfo placement;
    private final Item to;

    public AmmoStorageUpgradeRecipe(boolean diamond) {
        this.diamond = diamond;
        var center = diamond ? ModItems.AMMO_STORAGE_GOLD : ModItems.AMMO_STORAGE;
        this.to = diamond ? ModItems.AMMO_STORAGE_DIAMOND : ModItems.AMMO_STORAGE_GOLD;
        var edge = Ingredient.of(diamond ? Items.DIAMOND : Items.GOLD_INGOT);
        var cells = new ArrayList<>(Collections.nCopies(9, Optional.of(edge)));
        cells.set(4, Optional.of(Ingredient.of(center)));
        this.pattern = new ShapedRecipePattern(3, 3, List.copyOf(cells), Optional.empty());
        this.placement = PlacementInfo.createFromOptionals(pattern.ingredients());
    }

    @Override public boolean matches(CraftingInput input, Level level) { return pattern.matches(input); }
    @Override public boolean showNotification() { return false; }
    @Override public String group() { return "tacz:ammo_storage_upgrade"; }
    @Override public CraftingBookCategory category() { return CraftingBookCategory.MISC; }
    @Override public PlacementInfo placementInfo() { return placement; }

    /** Real vanilla recipe display; no duplicate/synthetic recipes in either viewer's cache. */
    @Override public List<RecipeDisplay> display() {
        return List.of(new ShapedCraftingRecipeDisplay(3, 3, pattern.ingredients().stream()
                .map(cell -> cell.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE)).toList(),
                new SlotDisplay.ItemSlotDisplay(to), new SlotDisplay.ItemSlotDisplay(Items.CRAFTING_TABLE)));
    }

    @Override
    public ItemStack assemble(CraftingInput input, net.minecraft.core.HolderLookup.Provider registries) {
        // Preserve stock (including legacy typed BE data) and the name; never copy arbitrary item overrides.
        var result = new ItemStack(to);
        var center = input.getItem(4);
        result.copyFrom(DataComponents.CUSTOM_DATA, center);
        result.copyFrom(DataComponents.BLOCK_ENTITY_DATA, center);
        result.copyFrom(DataComponents.CUSTOM_NAME, center);
        return result;
    }

    @Override
    public RecipeSerializer<AmmoStorageUpgradeRecipe> getSerializer() { return ModRecipe.AMMO_STORAGE_UPGRADE; }

    public static RecipeSerializer<AmmoStorageUpgradeRecipe> serializer() {
        return new RecipeSerializer<>() {
            @Override public com.mojang.serialization.MapCodec<AmmoStorageUpgradeRecipe> codec() {
                return Codec.BOOL.optionalFieldOf("diamond", false).xmap(AmmoStorageUpgradeRecipe::new, r -> r.diamond);
            }
            @Override public StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, AmmoStorageUpgradeRecipe> streamCodec() {
                return StreamCodec.of((buf, recipe) -> buf.writeBoolean(recipe.diamond), buf -> new AmmoStorageUpgradeRecipe(buf.readBoolean()));
            }
        };
    }
}
