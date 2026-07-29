package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public interface AmmoItemDataAccessor extends IAmmo {
    String AMMO_ID_TAG = "AmmoId";

    @Override
    @Nonnull
    default Identifier getAmmoId(ItemStack ammo) {
        CompoundTag nbt = ItemNbtUtils.getTag(ammo);
        if (nbt.contains(AMMO_ID_TAG)) {
            Identifier gunId = Identifier.tryParse(nbt.getStringOr(AMMO_ID_TAG, ""));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_AMMO_ID);
        }
        return DefaultAssets.EMPTY_AMMO_ID;
    }

    @Override
    default void setAmmoId(ItemStack ammo, @Nullable Identifier ammoId) {
        ItemNbtUtils.updateTag(ammo, nbt -> {
            if (ammoId != null) {
                nbt.putString(AMMO_ID_TAG, ammoId.toString());
            } else {
                nbt.putString(AMMO_ID_TAG, DefaultAssets.DEFAULT_AMMO_ID.toString());
            }
        });
        applyMaxStackSize(ammo);
    }

    /**
     * 按枪包数据写入 {@code minecraft:max_stack_size} 组件，修复「子弹不可堆叠」。
     *
     * <h2>问题根因</h2>
     * {@code AmmoItem} 的构造是 {@code super(properties.stacksTo(1))} —— 与上游一致，
     * 因为真正的堆叠上限是<b>每种弹药各不相同</b>的（来自枪包 {@code CommonAmmoIndex#getStackSize}），
     * 没法在物品注册时写死。
     *
     * <p>上游靠覆写 {@code Item#verifyComponentsAfterLoad(ItemStack)} 在物品载入后
     * 写入 {@code DataComponents.MAX_STACK_SIZE}。但 <b>26.2 的 {@code Item} 已没有这个方法</b>
     * （字节码确认），移植时改成了自定义的 {@code IItem#tacz$getMaxStackSize} +
     * {@code ItemStackMixin} 去改 {@code ItemStack#getMaxStackSize} 的返回值。
     *
     * <p><b>但那条路是死的，有两处独立失效：</b>
     * <ol>
     *   <li>{@code ItemStackMixin} <b>从未被注册</b>到任何 {@code *.mixins.json}
     *       （全仓 grep 零命中）→ 根本不会加载；</li>
     *   <li>即便注册，它的目标 {@code ItemStack#getMaxStackSize} 在 26.2 <b>也不存在</b>
     *       （字节码确认 {@code ItemStack} 只有 {@code getCount/setCount/limitSize/copyWithCount}），
     *       注册后反而会因找不到目标而<b>崩溃</b>。</li>
     * </ol>
     * 两者叠加的结果就是：所有弹药永远停在 {@code stacksTo(1)}。
     *
     * <h2>本修复</h2>
     * 26.2 里堆叠上限由 {@code DataComponents.MAX_STACK_SIZE} 组件决定（该组件确认存在）。
     * 这里在<b>写入弹药 ID 的同时</b>写入该组件 —— {@code setAmmoId} 是所有弹药物品
     * 获得身份的唯一入口（{@code AmmoItemBuilder#build}、换弹、合成、创造栏均经由此处），
     * 因此覆盖面等价于上游的 {@code verifyComponentsAfterLoad}，且无需 mixin。
     */
    static void applyMaxStackSize(ItemStack ammo) {
        if (!(ammo.getItem() instanceof IAmmo iAmmo)) {
            return;
        }
        // 【第 34 轮】上限必须夹到 [1, 99]。
        //
        // 26.2 的 max_stack_size 组件是 ExtraCodecs.intRange(1, 99)（与原版
        // Item.ABSOLUTE_MAX_STACK_SIZE 一致），枪包里若写了超过 99 的 stack_size，
        // 直接 set 进去会在<b>序列化/网络同步</b>时被 codec 拒绝，
        // 表现为物品异常甚至断线。这里先夹住，宁可少堆也不能崩。
        TimelessAPI.getCommonAmmoIndex(iAmmo.getAmmoId(ammo))
                .map(index -> Math.clamp(index.getStackSize(), 1, 99))
                .ifPresent(size -> ammo.set(DataComponents.MAX_STACK_SIZE, size));
    }

    @Override
    default boolean isAmmoOfGun(ItemStack gun, ItemStack ammo) {
        if (gun.getItem() instanceof IGun iGun && ammo.getItem() instanceof IAmmo iAmmo) {
            Identifier gunId = iGun.getGunId(gun);
            Identifier ammoId = iAmmo.getAmmoId(ammo);
            return TimelessAPI.getCommonGunIndex(gunId).map(gunIndex -> gunIndex.getGunData().getAmmoId().equals(ammoId)).orElse(false);
        }
        return false;
    }
}
