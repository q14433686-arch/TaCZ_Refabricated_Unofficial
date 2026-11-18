package com.tacz.guns.crafting;

import net.minecraft.core.component.DataComponents;
import com.mojang.serialization.Codec;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.init.ModRecipe;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

public class AmmoStorageUpgradeRecipe extends CustomRecipe {
    private final boolean diamond;
    private final Item from;
    private final Item material;
    private final Item to;

    public AmmoStorageUpgradeRecipe(boolean diamond) {
        this.diamond = diamond;
        if (diamond) {
            from = ModItems.AMMO_STORAGE_GOLD;
            material = Items.DIAMOND;
            to = ModItems.AMMO_STORAGE_DIAMOND;
        } else {
            from = ModItems.AMMO_STORAGE;
            material = Items.GOLD_INGOT;
            to = ModItems.AMMO_STORAGE_GOLD;
        }
    }

    @Override
    public boolean matches(CraftingInput container, Level level) {
        if (container.width() != 3 || container.height() != 3) {
            return false;
        }
        for (int slot = 0; slot < container.size(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (slot == 4) {
                if (!stack.is(from)) {
                    return false;
                }
            } else if (!stack.is(material)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
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
        return new RecipeSerializer<>(Codec.BOOL.optionalFieldOf("diamond", false).xmap(AmmoStorageUpgradeRecipe::new, r -> r.diamond),
                StreamCodec.of((buf, recipe) -> buf.writeBoolean(recipe.diamond), buf -> new AmmoStorageUpgradeRecipe(buf.readBoolean())));
    }
}
