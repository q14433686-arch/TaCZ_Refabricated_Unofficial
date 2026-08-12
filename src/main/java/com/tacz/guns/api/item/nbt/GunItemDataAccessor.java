package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public interface GunItemDataAccessor extends IGun {
    String GUN_ID_TAG = "GunId";
    String GUN_FIRE_MODE_TAG = "GunFireMode";
    String GUN_HAS_BULLET_IN_BARREL = "HasBulletInBarrel";
    String GUN_CURRENT_AMMO_COUNT_TAG = "GunCurrentAmmoCount";
    String GUN_ATTACHMENT_BASE = "Attachment";
    /**
     * 第 16 轮：26.2 的 ItemStack NBT 布局为 {@code {id, count, components:{...}}}。
     * 已安装配件自身的数据存在 components 下的 minecraft:custom_data 里。
     * 旧代码沿用 1.20.x 的 {@code "tag"} 子键，在 26.2 上恒查不到。
     */
    String COMPONENTS_TAG = "components";
    /** {@code DataComponents.CUSTOM_DATA.toString()} 的值，已实测确认。 */
    String CUSTOM_DATA_KEY = "minecraft:custom_data";
    String GUN_EXP_TAG = "GunLevelExp";
    String GUN_DUMMY_AMMO = "DummyAmmo";
    String GUN_MAX_DUMMY_AMMO = "MaxDummyAmmo";
    String GUN_ATTACHMENT_LOCK = "AttachmentLock";
    String GUN_DISPLAY_ID_TAG = "GunDisplayId";
    String LASER_COLOR_TAG = "LaserColor";
    String GUN_OVERHEAT_TAG = "HeatAmount";
    String GUN_OVERHEAT_LOCK_TAG = "OverHeated";

    @Override
    default boolean useDummyAmmo(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        return nbt.contains(GUN_DUMMY_AMMO);
    }

    @Override
    default int getDummyAmmoAmount(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        return Math.max(0, nbt.getIntOr(GUN_DUMMY_AMMO, 0));
    }

    @Override
    default void setDummyAmmoAmount(ItemStack gun, int amount) {
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putInt(GUN_DUMMY_AMMO, Math.max(amount, 0)));
    }

    @Override
    default void addDummyAmmoAmount(ItemStack gun, int amount) {
        if (!useDummyAmmo(gun)) {
            return;
        }
        int maxDummyAmmo = Integer.MAX_VALUE;
        if (hasMaxDummyAmmo(gun)) {
            maxDummyAmmo = getMaxDummyAmmoAmount(gun);
        }
        int finalMax = maxDummyAmmo;
        ItemNbtUtils.updateTag(gun, nbt -> {
            int newAmount = Math.min(nbt.getIntOr(GUN_DUMMY_AMMO, 0) + amount, finalMax);
            nbt.putInt(GUN_DUMMY_AMMO, Math.max(newAmount, 0));
        });
    }

    @Override
    default boolean hasMaxDummyAmmo(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        return nbt.contains(GUN_MAX_DUMMY_AMMO);
    }

    @Override
    default int getMaxDummyAmmoAmount(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        return Math.max(0, nbt.getIntOr(GUN_MAX_DUMMY_AMMO, 0));
    }

    @Override
    default void setMaxDummyAmmoAmount(ItemStack gun, int amount) {
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putInt(GUN_MAX_DUMMY_AMMO, Math.max(amount, 0)));
    }

    @Override
    default boolean hasAttachmentLock(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (nbt.contains(GUN_ATTACHMENT_LOCK)) {
            return nbt.getBooleanOr(GUN_ATTACHMENT_LOCK, false);
        }
        return false;
    }

    @Override
    default void setAttachmentLock(ItemStack gun, boolean lock) {
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putBoolean(GUN_ATTACHMENT_LOCK, lock));
    }

    @Override
    @Nonnull
    default Identifier getGunId(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (nbt.contains(GUN_ID_TAG)) {
            Identifier gunId = Identifier.tryParse(nbt.getStringOr(GUN_ID_TAG, ""));
            return Objects.requireNonNullElse(gunId, DefaultAssets.EMPTY_GUN_ID);
        }
        return DefaultAssets.EMPTY_GUN_ID;
    }

    @Override
    default void setGunId(ItemStack gun, @Nullable Identifier gunId) {
        ItemNbtUtils.updateTag(gun, nbt -> {
            if (gunId != null) {
                nbt.putString(GUN_ID_TAG, gunId.toString());
            }
        });
    }

    @Override
    @NotNull
    default Identifier getGunDisplayId(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (nbt.contains(GUN_DISPLAY_ID_TAG)) {
            Identifier gunDisplayId = Identifier.tryParse(nbt.getStringOr(GUN_DISPLAY_ID_TAG, ""));
            return Objects.requireNonNullElse(gunDisplayId, DefaultAssets.DEFAULT_GUN_DISPLAY_ID);
        }
        return DefaultAssets.DEFAULT_GUN_DISPLAY_ID;
    }

    @Override
    default void setGunDisplayId(ItemStack gun, Identifier displayId) {
        ItemNbtUtils.updateTag(gun, nbt -> {
            if (displayId != null) {
                nbt.putString(GUN_DISPLAY_ID_TAG, displayId.toString());
            }
        });
    }

    @Override
    default int getLevel(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (nbt.contains(GUN_EXP_TAG)) {
            return getLevel(nbt.getIntOr(GUN_EXP_TAG, 0));
        }
        return 0;
    }

    @Override
    default int getExp(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (nbt.contains(GUN_EXP_TAG)) {
            return nbt.getIntOr(GUN_EXP_TAG, 0);
        }
        return 0;
    }

    @Override
    default int getExpToNextLevel(ItemStack gun) {
        int exp = getExp(gun);
        int level = getLevel(exp);
        if (level >= getMaxLevel()) {
            return 0;
        }
        int nextLevelExp = getExp(level + 1);
        return nextLevelExp - exp;
    }

    @Override
    default int getExpCurrentLevel(ItemStack gun) {
        int exp = getExp(gun);
        int level = getLevel(exp);
        // getExp(level) is documented as the cumulative threshold at which this level starts.
        // The inherited level - 1 subtraction was inconsistent with getExpToNextLevel() and would
        // over-count one whole level as soon as a future gun implementation enables progression.
        return Math.max(0, exp - getExp(level));
    }

    @Override
    default FireMode getFireMode(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (nbt.contains(GUN_FIRE_MODE_TAG)) {
            return FireMode.valueOf(nbt.getStringOr(GUN_FIRE_MODE_TAG, "UNKNOWN"));
        }
        return FireMode.UNKNOWN;
    }

    @Override
    default void setFireMode(ItemStack gun, @Nullable FireMode fireMode) {
        ItemNbtUtils.updateTag(gun, nbt -> {
            if (fireMode != null) {
                nbt.putString(GUN_FIRE_MODE_TAG, fireMode.name());
            } else {
                nbt.putString(GUN_FIRE_MODE_TAG, FireMode.UNKNOWN.name());
            }
        });
    }

    @Override
    default int getCurrentAmmoCount(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (nbt.contains(GUN_CURRENT_AMMO_COUNT_TAG)) {
            return nbt.getIntOr(GUN_CURRENT_AMMO_COUNT_TAG, 0);
        }
        return 0;
    }

    @Override
    default void setCurrentAmmoCount(ItemStack gun, int ammoCount) {
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putInt(GUN_CURRENT_AMMO_COUNT_TAG, Math.max(ammoCount, 0)));
    }

    @Override
    default void reduceCurrentAmmoCount(ItemStack gun) {
        // 只在不使用背包直读的情况下减少 AmmoCount
        if (!useInventoryAmmo(gun)) {
            setCurrentAmmoCount(gun, getCurrentAmmoCount(gun) - 1);
        }
    }

    @Override
    /**
     * 读取已安装配件自身的 custom_data 标签。
     *
     * <h2>第 16 轮修复：{@code "tag"} 是 1.20.x 的旧 NBT 布局</h2>
     *
     * 原实现找的是 {@code allItemStackTag.contains("tag")}，那是<b>物品组件化之前</b>
     * （1.20.4 及更早）的 ItemStack NBT 结构 {@code {id, Count, tag:{...}}}。
     *
     * <p>26.2 的 {@code ItemStack} 序列化结果是 {@code {id, count, components:{...}}}，
     * <b>顶层根本没有 "tag" 键</b>（已实测：顶层键为 {@code [count, id]}）。
     * 于是本方法<b>恒返回 null</b> → {@link #getAttachmentId} 恒返回
     * {@code EMPTY_ATTACHMENT_ID} → <b>所有已安装的配件都被判定为「没装」</b>。
     *
     * <p>这一处 bug 同时解释了第 16 轮反馈的多个现象：
     * <ul>
     *   <li>瞄准镜装上去不被承认（{@code FirstPersonRenderGunEvent} 判定 scopeId 为空 → 走机瞄）</li>
     *   <li>扩容弹匣完全不生效（{@code getMagExtendLevel} 拿不到 id → 恒为 0 级）</li>
     *   <li>各类特殊弹匣插件（燃烧弹等）不生效（同上）</li>
     * </ul>
     * 而镭射之所以「看起来生效」，是因为它走的是独立的模型渲染路径，不依赖本方法。
     *
     * <p>正确布局（与上游 1.21.1 一致）：
     * {@code <配件槽键> -> "components" -> "minecraft:custom_data"}。
     */
    @Nullable
    default CompoundTag getAttachmentTag(ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return null;
        }
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        String key = GUN_ATTACHMENT_BASE + type.name();
        if (!nbt.contains(key)) {
            return null;
        }
        CompoundTag stackTag = nbt.getCompoundOrEmpty(key);
        if (!stackTag.contains(COMPONENTS_TAG)) {
            return null;
        }
        CompoundTag components = stackTag.getCompoundOrEmpty(COMPONENTS_TAG);
        if (!components.contains(CUSTOM_DATA_KEY)) {
            return null;
        }
        return components.getCompoundOrEmpty(CUSTOM_DATA_KEY);
    }

    /**
     * 写回已安装配件自身的 custom_data 标签（用于切换瞄具倍率等）。
     *
     * <p>第 16 轮新增：本方法在移植时<b>整个丢失了</b>（上游 1.21.1 有），
     * 导致任何需要修改「已安装配件」自身数据的功能都无法持久化，
     * 典型表现就是瞄具倍率切换后不生效 / 切枪后复原。
     */
    @Override
    default void setAttachmentTag(ItemStack gun, AttachmentType type, CompoundTag attachmentTag) {
        if (!allowAttachmentType(gun, type)) {
            return;
        }
        ItemNbtUtils.updateTag(gun, nbt -> {
            String key = GUN_ATTACHMENT_BASE + type.name();
            if (!nbt.contains(key)) {
                return;
            }
            CompoundTag stackTag = nbt.getCompoundOrEmpty(key);
            if (!stackTag.contains(COMPONENTS_TAG)) {
                return;
            }
            CompoundTag components = stackTag.getCompoundOrEmpty(COMPONENTS_TAG);
            if (!components.contains(CUSTOM_DATA_KEY)) {
                return;
            }
            components.put(CUSTOM_DATA_KEY, attachmentTag);
            stackTag.put(COMPONENTS_TAG, components);
            nbt.put(key, stackTag);
        });
    }

    @Override
    @NotNull
    default ItemStack getBuiltinAttachment(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return ItemStack.EMPTY;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElse(null);
        if (index != null) {
            var builtin = index.getGunData().getBuiltInAttachments();
            if (builtin.containsKey(type)) {
                return AttachmentItemBuilder.create().setId(builtin.get(type)).build();
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    default ItemStack getAttachment(ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return ItemStack.EMPTY;
        }
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        String key = GUN_ATTACHMENT_BASE + type.name();
        if (nbt.contains(key)) {
            return ItemNbtUtils.loadItemStack(nbt.getCompoundOrEmpty(key));
        }
        return ItemStack.EMPTY;
    }

    @Override
    @NotNull
    default Identifier getBuiltInAttachmentId(ItemStack gun, AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID;
        }
        CommonGunIndex index = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElse(null);
        if (index != null) {
            var builtin = index.getGunData().getBuiltInAttachments();
            if (builtin.containsKey(type)) {
                return builtin.get(type);
            }
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    @Override
    @Nonnull
    default Identifier getAttachmentId(ItemStack gun, AttachmentType type) {
        CompoundTag attachmentTag = this.getAttachmentTag(gun, type);
        if (attachmentTag != null) {
            return AttachmentItemDataAccessor.getAttachmentIdFromTag(attachmentTag);
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    @Override
    default void installAttachment(@Nonnull ItemStack gun, @Nonnull ItemStack attachment) {
        if (!allowAttachment(gun, attachment)) {
            return;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachment);
        if (iAttachment == null) {
            return;
        }
        String key = GUN_ATTACHMENT_BASE + iAttachment.getType(attachment).name();
        CompoundTag attachmentTag = ItemNbtUtils.saveItemStack(attachment);
        ItemNbtUtils.updateTag(gun, nbt -> nbt.put(key, attachmentTag));
    }

    @Override
    default void unloadAttachment(@Nonnull ItemStack gun, AttachmentType type) {
        if (!allowAttachmentType(gun, type)) {
            return;
        }
        String key = GUN_ATTACHMENT_BASE + type.name();
        CompoundTag attachmentTag = ItemNbtUtils.saveItemStack(ItemStack.EMPTY);
        ItemNbtUtils.updateTag(gun, nbt -> nbt.put(key, attachmentTag));
    }

    @Override
    default float getAimingZoom(ItemStack gunItem) {
        float zoom = 1;
        Identifier scopeId = this.getAttachmentId(gunItem, AttachmentType.SCOPE);
        boolean builtin = false;
        if (scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeId = getBuiltInAttachmentId(gunItem, AttachmentType.SCOPE);
            builtin = true;
        }
        if (!DefaultAssets.isEmptyAttachmentId(scopeId)) {
            CompoundTag attachmentTag = this.getAttachmentTag(gunItem, AttachmentType.SCOPE);
            int zoomNumber = builtin ? 0 : AttachmentItemDataAccessor.getZoomNumberFromTag(attachmentTag);
            float[] zooms = TimelessAPI.getClientAttachmentIndex(scopeId).map(ClientAttachmentIndex::getZoom).orElse(null);
            if (zooms != null) {
                zoom = zooms[zoomNumber % zooms.length];
            }
        } else {
            zoom = TimelessAPI.getGunDisplay(gunItem).map(GunDisplayInstance::getIronZoom).orElse(1f);
        }
        return zoom;
    }

    @Override
    default boolean hasBulletInBarrel(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (nbt.contains(GUN_HAS_BULLET_IN_BARREL)) {
            return nbt.getBooleanOr(GUN_HAS_BULLET_IN_BARREL, false);
        }
        return false;
    }

    @Override
    default void setBulletInBarrel(ItemStack gun, boolean bulletInBarrel) {
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putBoolean(GUN_HAS_BULLET_IN_BARREL, bulletInBarrel));
    }

    @Override
    default boolean hasCustomLaserColor(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        return nbt.contains(LASER_COLOR_TAG);
    }

    @Override
    default int getLaserColor(ItemStack gun) {
        CompoundTag nbt = ItemNbtUtils.getTag(gun);
        if (!hasCustomLaserColor(gun)) {
            return 0xFF0000;
        }
        return nbt.getIntOr(LASER_COLOR_TAG, 0xFF0000);
    }

    @Override
    default void setLaserColor(ItemStack gun, int color) {
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putInt(LASER_COLOR_TAG, color));
    }

    /**
     * Heat Data
     */
    @Override
    default boolean hasHeatData(ItemStack gun) {
        return ItemNbtUtils.getTag(gun).contains(GUN_OVERHEAT_TAG);
    }

    @Override
    default boolean isOverheatLocked(ItemStack gun) {
        return ItemNbtUtils.getTag(gun).getBooleanOr(GUN_OVERHEAT_LOCK_TAG, false);
    }

    @Override
    default void setOverheatLocked(ItemStack gun, boolean locked) {
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putBoolean(GUN_OVERHEAT_LOCK_TAG, locked));
    }

    @Override
    default float getHeatAmount(ItemStack gun) {
        if (hasHeatData(gun)) return ItemNbtUtils.getTag(gun).getFloatOr(GUN_OVERHEAT_TAG, 0f);
        return 0f;
    }

    @Override
    default void setHeatAmount(ItemStack gun, float amount) {
        ItemNbtUtils.updateTag(gun, nbt -> nbt.putFloat(GUN_OVERHEAT_TAG, Math.max(amount, 0f)));
    }

    @Override
    default float lerpRPM(ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(getGunId(gun))
                .map(index -> index.getGunData().getHeatData())
                .map(heatData -> {
                    float heatPercentage = (getHeatAmount(gun) / heatData.getHeatMax());
                    return Mth.lerp(heatPercentage, heatData.getMinRpmMod(), heatData.getMaxRpmMod());
                }).orElse(1f);
    }

    @Override
    default float lerpInaccuracy(ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(getGunId(gun))
                .map(index -> index.getGunData().getHeatData())
                .map(heatData -> {
                    float heatPercentage = (getHeatAmount(gun) / heatData.getHeatMax());
                    return Mth.lerp(heatPercentage, heatData.getMinInaccuracy(), heatData.getMaxInaccuracy());
                }).orElse(1f);
    }
}
