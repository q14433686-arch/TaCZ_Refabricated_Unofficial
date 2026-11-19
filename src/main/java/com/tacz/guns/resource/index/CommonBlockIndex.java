package com.tacz.guns.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.pojo.BlockIndexPOJO;
import com.tacz.guns.resource.pojo.data.block.BlockData;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;

import java.util.List;

public class CommonBlockIndex {

    private BlockIndexPOJO pojo;
    private BlockItem block;
    private BlockData data;
    private RecipeFilter filter;

    public static CommonBlockIndex getInstance(BlockIndexPOJO pojo) throws IllegalArgumentException {
        return getInstance(pojo, CommonAssetsManager.get());
    }

    public static CommonBlockIndex getInstance(BlockIndexPOJO pojo, ICommonResourceProvider resources) {
        CommonBlockIndex index = new CommonBlockIndex();
        index.pojo = pojo;
        checkIndex(pojo, index);
        checkData(pojo, index, resources);
        return index;
    }

    private static void checkIndex(BlockIndexPOJO block, CommonBlockIndex index) {
        Preconditions.checkArgument(block != null, "index object file is empty");
        Identifier id = block.getId();
        if (!(BuiltInRegistries.ITEM.getOptional(id).orElse(null) instanceof BlockItem item)) {
            throw new IllegalArgumentException("BlockItem not found for " + block.getName());
        }
        index.block = item;
    }

    private static void checkData(BlockIndexPOJO block, CommonBlockIndex index, ICommonResourceProvider resources) {
        Identifier pojoData = block.getData();
        Preconditions.checkArgument(pojoData != null, "index object missing pojoData field");
        BlockData data = resources.getBlockData(pojoData);
        Preconditions.checkArgument(data != null, "there is no corresponding data file");
        RecipeFilter recipeFilter = resources.getRecipeFilter(data.getFilter());
        Preconditions.checkArgument(recipeFilter != null, "there is no corresponding data file");
        index.data = data;
        index.filter = recipeFilter;
    }

    public BlockIndexPOJO getPojo() {
        return pojo;
    }

    public BlockItem getBlock() {
        return block;
    }

    public BlockData getData() {
        return data;
    }

    /** Menu, screen and recipe viewers must apply the same server-configured table policy. */
    public List<TabConfig> recipeTabs(Identifier tableId) {
        return unfilteredDefault(tableId) ? TabConfig.DEFAULT_TABS : data.getTabs();
    }

    public boolean allowsRecipe(Identifier tableId, Identifier recipeId) {
        return unfilteredDefault(tableId) || filter.contains(recipeId);
    }

    private static boolean unfilteredDefault(Identifier tableId) {
        return DefaultAssets.DEFAULT_BLOCK_ID.equals(tableId) && !SyncConfig.ENABLE_TABLE_FILTER.get();
    }

    public RecipeFilter getFilter() {
        return filter;
    }
}
