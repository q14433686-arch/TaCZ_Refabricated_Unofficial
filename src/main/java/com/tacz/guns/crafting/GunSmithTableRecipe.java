package com.tacz.guns.crafting;

import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import java.util.List;

public class GunSmithTableRecipe implements Recipe<SingleRecipeInput> {
    private final Identifier id;
    private final GunSmithTableResult result;
    private final List<GunSmithTableIngredient> inputs;

    public GunSmithTableRecipe(Identifier id, GunSmithTableResult result, List<GunSmithTableIngredient> inputs) {
        this.id = id;
        this.result = result;
        this.inputs = inputs;
    }

    public GunSmithTableRecipe(Identifier id, TableRecipe tableRecipe) {
        this(id, tableRecipe.getResult(), tableRecipe.getMaterials());
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    /**
     * 枪械工作台配方只由 {@link com.tacz.guns.inventory.GunSmithTableMenu} 消费，不进原版
     * 3×3 合成格。26.2 的 {@code RecipeManager#finalizeRecipeLoading} 对每条配方做
     * {@code !recipe.isSpecial() && recipe.placementInfo().isImpossibleToPlace()} 判定，
     * 命中即打一行 {@code "Recipe … can't be placed due to empty ingredients"} WARN ——
     * 本类返回 {@link PlacementInfo#NOT_PLACEABLE}，所以此前每条 tacz:/lrtactical: 配方
     * 都会刷一行（默认枪包约 250 行）。标成 special 后 RecipeManager 跳过这条 placement
     * 检查；工作台自己的材料校验与合成不经过 placementInfo，行为不变。
     * 与 1.21.11 分支 {@code 1d8174db} 同源。
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<? extends Recipe<SingleRecipeInput>> getSerializer() {
        return (RecipeSerializer) ModRecipe.GUN_SMITH_TABLE_RECIPE_SERIALIZER;
    }

    @Override
    public RecipeType<? extends Recipe<SingleRecipeInput>> getType() {
        return (RecipeType) ModRecipe.GUN_SMITH_TABLE_CRAFTING;
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return new RecipeBookCategory();
    }

    public ItemStack getOutput() {
        return result.getResult();
    }

    public List<GunSmithTableIngredient> getInputs() {
        return inputs;
    }

    public GunSmithTableResult getResult() {
        return result;
    }

    public void init() {
        result.init();
    }

    public Identifier getTab() {
        return result.getGroup();
    }

    public Identifier getId() {
        return this.id;
    }
}
