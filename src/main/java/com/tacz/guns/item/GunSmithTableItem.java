package com.tacz.guns.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.BlockItemBuilder;
import com.tacz.guns.api.item.nbt.BlockItemDataAccessor;
import com.tacz.guns.client.renderer.item.GunSmithTableItemRenderer;
import com.tacz.guns.inventory.tooltip.BlockItemTooltip;
import com.tacz.guns.resource.index.CommonBlockIndex;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import java.util.Optional;

public class GunSmithTableItem extends BlockItem implements BlockItemDataAccessor, IItem {
    public GunSmithTableItem(Block block, Item.Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Environment(EnvType.CLIENT)
    @Override
    public BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return GunSmithTableItemRenderer.INSTANCE.get();
    }

    public static NonNullList<ItemStack> fillItemCategory() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        TimelessAPI.getAllCommonBlockIndex().forEach((blockIndex) -> {
            ItemStack stack = BlockItemBuilder.create(blockIndex.getValue().getBlock()).setId(blockIndex.getKey()).build();
            stacks.add(stack);
        });
        return stacks;
    }

    /**
     * 获取枪械制造台（方块物品）的显示名称。
     *
     * <p>{@code Item#getName(ItemStack)} 是双端公共方法（{@code /give} 回执、容器标题、
     * 铁砧改名等服务端路径都会调用），因此<b>不能</b>读只在客户端存在的
     * {@code ClientBlockIndex}：挂 {@code @Environment(CLIENT)} 会被 fabric-loader 在专服上
     * 把整个方法剥离（枪包名字静默退化成原版兜底名），只删注解不改实现则会
     * {@code NoClassDefFoundError}。完整推导见
     * {@link com.tacz.guns.api.item.gun.AbstractGunItem#getName(net.minecraft.world.item.ItemStack)}。
     *
     * <p>common/client 两侧索引取的是同一份 index json 的同一个 {@code name} 键，
     * 客户端显示结果不变。
     */
    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        Identifier blockId = this.getBlockId(stack);
        Optional<CommonBlockIndex> blockIndex = TimelessAPI.getCommonBlockIndex(blockId);
        if (blockIndex.isPresent() && blockIndex.get().getPojo() != null) {
            String name = blockIndex.get().getPojo().getName();
            return Component.translatable(StringUtils.isBlank(name) ? "custom.tacz.error.no_name" : name);
        }
        return super.getName(stack);
    }

//    @Override
//    @Environment(EnvType.CLIENT)
//    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> components, TooltipFlag isAdvanced) {
//        Identifier blockId = this.getBlockId(stack);
//        TimelessAPI.getClientBlockIndex(blockId).ifPresent(index -> {
//            String tooltipKey = index.getTooltipKey();
//            if (tooltipKey != null) {
//                components.add(Component.translatable(tooltipKey).withStyle(style -> style.withColor(0xAAAAAA)));
//            }
//        });
//
//        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(blockId);
//        if (packInfoObject != null) {
//            MutableComponent component = Component.translatable(packInfoObject.getName()).withStyle(style -> style.withColor(0x5555FF)).withStyle(style -> style.withItalic(true));
//            components.add(component);
//        }
//    }

    @Override
    @NotNull
    public Optional<TooltipComponent> getTooltipImage(ItemStack pStack) {
        return Optional.of(new BlockItemTooltip(this.getBlockId(pStack)));
    }
}
