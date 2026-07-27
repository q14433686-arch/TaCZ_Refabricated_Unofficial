package com.tacz.guns.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.nbt.AmmoItemDataAccessor;
import com.tacz.guns.client.renderer.item.AmmoItemRenderer;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.resource.index.CommonAmmoIndex;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class AmmoItem extends Item implements AmmoItemDataAccessor, IItem {
    public AmmoItem(Properties properties) {
        // 【第 34 轮】不再 stacksTo(1)。
        //
        // 上一轮把 MAX_STACK_SIZE 组件写在 setAmmoId 里，机制本身是对的
        // （26.2 的 ItemInstance#getMaxStackSize 就是读 DataComponents.MAX_STACK_SIZE，
        //  字节码确认：getOrDefault(MAX_STACK_SIZE, 1)），但仍然不生效，原因有两个：
        //
        // 1. <b>物品注册期就把上限钉死成 1 了。</b> {@code Properties#stacksTo(n)} 的实现
        //    就是 {@code component(MAX_STACK_SIZE, n)}（字节码确认），
        //    它写的是物品的<b>默认组件集(prototype)</b>。而 {@code applyMaxStackSize} 依赖
        //    {@code TimelessAPI.getCommonAmmoIndex(...)} 查枪包数据 ——
        //    这份数据来自资源重载，在<b>客户端刚进服/尚未同步</b>时可能还是空的，
        //    此时 {@code ifPresent} 不执行，stack 就保持 prototype 的 1。
        //    只要有<b>任何一次</b>落空，那一组子弹就永久停在 1。
        //
        // 2. <b>patch 参与相等性判断</b>（{@code PatchedDataComponentMap#equals} 同时比较
        //    prototype 与 patch，字节码确认）。prototype=1 时，写过组件的子弹带
        //    {@code patch{MAX_STACK_SIZE=36}}、没写过的 patch 为空，
        //    {@code isSameItemSameComponents} 直接 false ——
        //    两堆<b>看起来一样</b>的子弹永远不合并。
        //
        // 3. 存档里<b>已经存在</b>的子弹根本不会再经过 setAmmoId（它只在生成物品时调用），
        //    所以老物品永远停在 1。需要配合下面的 inventoryTick 自愈。
        //
        // 因此把物品级默认上限抬到 99：即便枪包数据一时查不到，也能正常堆叠，
        // 单个弹种的精确上限再由 setAmmoId / inventoryTick 写入。
        super(properties.stacksTo(MAX_AMMO_STACK_SIZE));
    }

    /**
     * 物品级默认堆叠上限。
     *
     * <p>取 99 —— 与原版 {@code Item.ABSOLUTE_MAX_STACK_SIZE} 一致。这只是个<b>兜底</b>：
     * 每个弹种的真实上限由 {@link AmmoItemDataAccessor#applyMaxStackSize} 按
     * {@code CommonAmmoIndex#getStackSize} 逐个写入。之所以不设成 1，
     * 是为了让「组件缺失的老物品」也能先堆起来，而不是卡在 1。</p>
     */
    public static final int MAX_AMMO_STACK_SIZE = 99;

    /**
     * 【第 34 轮】老存档里的子弹自愈。
     *
     * <p>{@code setAmmoId} 只在<b>生成</b>弹药时调用，已经躺在玩家背包/箱子里的旧子弹
     * 不会再经过它，因此永远缺少正确的 {@code MAX_STACK_SIZE} 组件。
     * 这里在物品 tick 时补写一次，代价极低（组件相同时 {@code set} 不产生变化）。</p>
     *
     * <p>26.2 的签名是 {@code (ItemStack, ServerLevel, Entity, EquipmentSlot)}，
     * 已对字节码确认；它<b>只在服务端</b>调用（{@code ItemStack#inventoryTick} 里
     * 有 {@code instanceof ServerLevel} 门禁），组件变更会随物品同步到客户端。</p>
     */
    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull ServerLevel level,
                              @Nonnull Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        AmmoItemDataAccessor.applyMaxStackSize(stack);
    }

    // 【第 39 轮】移除 tacz$getMaxStackSize 覆写。
    // 该扩展点依赖 compat/tweakeroo/ItemMixin 注入 Item#getMaxStackSize(ItemStack)，
    // 而 26.2 的 Item 根本没有这个重载（只有无参 getDefaultMaxStackSize），
    // mixin 注册即崩、故从未启用。堆叠上限自第 34 轮起改由
    // DataComponents.MAX_STACK_SIZE 组件承担（见构造器与 inventoryTick），
    // 本方法已无任何调用方。

    @Override
    @Nonnull
    @Environment(EnvType.CLIENT)
    public Component getName(@Nonnull ItemStack stack) {
        Identifier ammoId = this.getAmmoId(stack);
        Optional<ClientAmmoIndex> ammoIndex = TimelessAPI.getClientAmmoIndex(ammoId);
        if (ammoIndex.isPresent()) {
            return Component.translatable(ammoIndex.get().getName());
        }
        return super.getName(stack);
    }

    private static Comparator<Map.Entry<Identifier, CommonAmmoIndex>> idNameSort() {
        return Comparator.comparingInt(m -> m.getValue().getSort());
    }

    public static NonNullList<ItemStack> fillItemCategory() {
        NonNullList<ItemStack> stacks = NonNullList.create();
        TimelessAPI.getAllCommonAmmoIndex().stream().sorted(idNameSort()).forEach(entry -> {
            ItemStack itemStack = AmmoItemBuilder.create().setId(entry.getKey()).build();
            stacks.add(itemStack);
        });
        return stacks;
    }

    @Override
    @Environment(EnvType.CLIENT)
    public BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return AmmoItemRenderer.INSTANCE.get();
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> adder, TooltipFlag isAdvanced) {
        Identifier ammoId = this.getAmmoId(stack);
        TimelessAPI.getClientAmmoIndex(ammoId).ifPresent(index -> {
            String tooltipKey = index.getTooltipKey();
            if (tooltipKey != null) {
                adder.accept(Component.translatable(tooltipKey).withStyle(style -> style.withColor(0xAAAAAA)));
            }
        });

        PackInfo packInfoObject = ClientAssetsManager.INSTANCE.getPackInfo(ammoId);
        if (packInfoObject != null) {
            MutableComponent component = Component.translatable(packInfoObject.getName()).withStyle(style -> style.withColor(0x5555FF)).withStyle(style -> style.withItalic(true));
            adder.accept(component);
        }
    }
}
