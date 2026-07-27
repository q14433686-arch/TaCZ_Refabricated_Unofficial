package com.tacz.guns.item;

import cn.sh1rocu.tacz.api.extension.IItem;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAmmoBox;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.nbt.AmmoBoxItemDataAccessor;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.inventory.tooltip.AmmoBoxTooltip;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import java.util.Optional;
import java.util.function.Consumer;

public class AmmoBoxItem extends Item implements AmmoBoxItemDataAccessor, IItem {

    public static final int IRON_LEVEL = 0;
    public static final int GOLD_LEVEL = 1;
    public static final int DIAMOND_LEVEL = 2;

    public AmmoBoxItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack pOther, Slot slot, ClickAction action, Player player, SlotAccess access) {
        return super.overrideOtherStackedOnMe(stack, pOther, slot, action, player, access);
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack ammoBox, Slot slot, ClickAction action, Player player) {
        // 右击
        if (action == ClickAction.SECONDARY) {
            // 点击的格子
            ItemStack slotItem = slot.getItem();
            Identifier boxAmmoId = this.getAmmoId(ammoBox);

            // 格子为空，那就是取出物品
            if (slotItem.isEmpty()) {
                // 创造模式弹药箱不能取出任何东西
                if (isAllTypeCreative(ammoBox) || isCreative(ammoBox)) {
                    return false;
                }
                // 啥也没有，不能取出
                if (boxAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                    return false;
                }
                // 数量不对，不能取出
                int boxAmmoCount = this.getAmmoCount(ammoBox);
                if (boxAmmoCount <= 0) {
                    return false;
                }
                return TimelessAPI.getCommonAmmoIndex(boxAmmoId).map(index -> {
                    int takeCount = Math.min(index.getStackSize(), boxAmmoCount);
                    ItemStack takeAmmo = AmmoItemBuilder.create().setId(boxAmmoId).setCount(takeCount).build();
                    ItemStack remainingAmmo = slot.safeInsert(takeAmmo);
                    int insertedCount = takeCount - remainingAmmo.getCount();
                    if (insertedCount <= 0) {
                        return false;
                    }

                    int remainCount = boxAmmoCount - insertedCount;
                    this.setAmmoCount(ammoBox, remainCount);
                    if (remainCount <= 0) {
                        this.setAmmoId(ammoBox, DefaultAssets.EMPTY_AMMO_ID);
                    }
                    this.playRemoveOneSound(player);
                    return true;
                }).orElse(false);
            }

            // 如果是子弹
            if (slotItem.getItem() instanceof IAmmo iAmmo) {
                // 全类型弹药箱不能存入
                if (isAllTypeCreative(ammoBox)) {
                    return false;
                }
                Identifier slotAmmoId = iAmmo.getAmmoId(slotItem);
                // 格子里的子弹 ID 不对，不能放
                if (slotAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                    return false;
                }
                // 如果盒子的子弹 ID 为空，变成当前点击的类型
                if (boxAmmoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
                    this.setAmmoId(ammoBox, slotAmmoId);
                } else if (!slotAmmoId.equals(boxAmmoId)) {
                    return false;
                }
                TimelessAPI.getCommonAmmoIndex(slotAmmoId).ifPresent(index -> {
                    // 创造模式弹药箱，那就直接存入最大
                    if (isCreative(ammoBox)) {
                        this.setAmmoCount(ammoBox, Integer.MAX_VALUE);
                        return;
                    }
                    int boxAmmoCount = this.getAmmoCount(ammoBox);
                    int boxLevelMultiplier = this.getAmmoLevel(ammoBox) + 1;
                    int maxSize = index.getStackSize() * SyncConfig.AMMO_BOX_STACK_SIZE.get() * boxLevelMultiplier;
                    int needCount = maxSize - boxAmmoCount;
                    ItemStack takeItem = slot.safeTake(slotItem.getCount(), needCount, player);
                    this.setAmmoCount(ammoBox, boxAmmoCount + takeItem.getCount());
                });
                // 播放取出声音
                this.playInsertSound(player);
                return true;
            }
        }
        return false;
    }

    private void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        if (isAllTypeCreative(stack) || isCreative(stack)) {
            return false;
        }
        return !this.getAmmoId(stack).equals(DefaultAssets.EMPTY_AMMO_ID) && this.getAmmoCount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Identifier ammoId = this.getAmmoId(stack);
        int ammoCount = this.getAmmoCount(stack);
        int boxLevelMultiplier = this.getAmmoLevel(stack) + 1;
        double widthPercent = TimelessAPI.getCommonAmmoIndex(ammoId).map(index -> {
            double totalCount = index.getStackSize() * SyncConfig.AMMO_BOX_STACK_SIZE.get() * boxLevelMultiplier;
            return ammoCount / totalCount;
        }).orElse(0d);
        return (int) Math.min(1 + 12 * widthPercent, 13);
    }

    @Override
    public Component getName(ItemStack stack) {
        if (isAllTypeCreative(stack)) {
            return Component.translatable("item.tacz.ammo_box.all_type_creative").withStyle(style -> style.withColor(0xAA00AA));
        }
        if (isCreative(stack)) {
            return Component.translatable("item.tacz.ammo_box.creative").withStyle(style -> style.withColor(0xAA00AA));
        }
        int ammoLevel = getAmmoLevel(stack);
        switch (ammoLevel) {
            case GOLD_LEVEL -> {
                return Component.translatable("item.tacz.ammo_box.gold").withStyle(style -> style.withColor(0xFFFF55));
            }
            case DIAMOND_LEVEL -> {
                return Component.translatable("item.tacz.ammo_box.diamond").withStyle(style -> style.withColor(0x55FFFF));
            }
            default -> {
                return Component.translatable("item.tacz.ammo_box.iron");
            }
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        if (isAllTypeCreative(stack) || isCreative(stack)) {
            return true;
        }
        return super.isFoil(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(1 / 3f, 1.0F, 1.0F);
    }

    public static void fillItemCategory(CreativeModeTab.Output output) {
        ItemStack ammoBox = ModItems.AMMO_BOX.getDefaultInstance();
        if (ammoBox.getItem() instanceof IAmmoBox iAmmoBox) {
            // 添加普通版本的弹药盒
            output.accept(iAmmoBox.setAmmoLevel(ammoBox.copy(), IRON_LEVEL));
            output.accept(iAmmoBox.setAmmoLevel(ammoBox.copy(), GOLD_LEVEL));
            output.accept(iAmmoBox.setAmmoLevel(ammoBox.copy(), DIAMOND_LEVEL));

            // 添加创造模式弹药盒
            output.accept(iAmmoBox.setCreative(ammoBox.copy(), false));
            output.accept(iAmmoBox.setCreative(ammoBox.copy(), true));
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        if (!(stack.getItem() instanceof IAmmoBox iAmmoBox)) {
            return Optional.empty();
        }
        Identifier ammoId = iAmmoBox.getAmmoId(stack);
        if (ammoId.equals(DefaultAssets.EMPTY_AMMO_ID)) {
            return Optional.empty();
        }
        int ammoCount = iAmmoBox.getAmmoCount(stack);
        if (ammoCount <= 0) {
            return Optional.empty();
        }
        ItemStack ammoStack = AmmoItemBuilder.create().setId(ammoId).build();
        return Optional.of(new AmmoBoxTooltip(stack, ammoStack, ammoCount));
    }

    /**
     * 弹药盒<b>不使用</b>自定义渲染器 —— 它走原版模型渲染。
     *
     * <p>外观变体由 {@code assets/tacz/items/ammo_box.json} 里的
     * {@code minecraft:select} + {@code tacz:ammo_statue} 属性
     * （见 {@code AmmoBoxStatueProperty}）在 9 个
     * {@code models/item/ammo_box/*.json} 之间切换，染色由模型里的
     * {@code minecraft:dye} tint 完成。这与上游 1.21.1 的做法一致 ——
     * 上游同样没有弹药盒渲染器，只有 {@code ItemProperties.register} + overrides。
     *
     * <p>此前这里返回过 {@code AmmoBoxItemRenderer}，它把 128×128 的
     * <b>3D 模型 UV 展开图</b>当平面图标贴在 16×16 四边形上，
     * 导致物品栏与模型贴图全是错乱色块。该类已随本次修改删除。
     */
    @Override
    @Environment(EnvType.CLIENT)
    public BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> adder, TooltipFlag isAdvanced) {
        if (isAllTypeCreative(stack)) {
            adder.accept(Component.translatable("tooltip.tacz.ammo_box.usage.all_type_creative").withStyle(style -> style.withColor(0xFFAA00)));
            return;
        }
        if (isCreative(stack)) {
            adder.accept(Component.translatable("tooltip.tacz.ammo_box.usage.creative.1").withStyle(style -> style.withColor(0xFFFF55)));
            adder.accept(Component.translatable("tooltip.tacz.ammo_box.usage.creative.2").withStyle(style -> style.withColor(0xFFFF55)));
            return;
        }
        adder.accept(Component.translatable("tooltip.tacz.ammo_box.usage.deposit").withStyle(style -> style.withColor(0xAAAAAA)));
        adder.accept(Component.translatable("tooltip.tacz.ammo_box.usage.remove").withStyle(style -> style.withColor(0xAAAAAA)));
    }
}
