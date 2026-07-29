package me.xjqsh.lrtactical.api.index;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 「数据驱动物品」的索引。
 *
 * <p>本模组的所有内容（手雷 / 刀 / 消耗品）都共用少数几个基础物品，
 * 具体是哪一种由数据包里的一条 index 定义决定。本接口就是那条定义的运行时形态。
 *
 * <p>26.2 变更：{@code ResourceLocation} → {@link Identifier}。
 */
public interface ICustomItemIndex {
    ItemStack createItemStack();

    int getMaxStackSize();

    Identifier getId();

    Item getBaseItem();

    String getDescriptionId();
}
