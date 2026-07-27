package com.tacz.guns.util;

import com.mojang.serialization.DataResult;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * Utility class for accessing ItemStack custom data in MC 26.2+.
 * Replaces the removed getOrCreateTag()/getTag()/hasTag() methods.
 */
public final class ItemNbtUtils {
    private static RegistryOps<Tag> nbtOps;

    private ItemNbtUtils() {
    }

    private static RegistryOps<Tag> getOps() {
        if (nbtOps == null) {
            RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            nbtOps = RegistryOps.create(NbtOps.INSTANCE, access);
        }
        return nbtOps;
    }

    /**
     * Get a copy of the item's custom data tag. Returns an empty CompoundTag if none exists.
     */
    public static CompoundTag getTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty()) {
            return data.copyTag();
        }
        return new CompoundTag();
    }

    /**
     * Update the item's custom data tag in-place.
     */
    public static void updateTag(ItemStack stack, Consumer<CompoundTag> consumer) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, consumer);
    }

    /**
     * Serialize an ItemStack to a CompoundTag using Codec.
     *
     * <p><b>第 15 轮修复</b>：必须用 {@link ItemStack#OPTIONAL_CODEC} 而非 {@code ItemStack.CODEC}。
     * 26.2 的 {@code ItemStack.MAP_CODEC} 里 count 字段是
     * {@code ExtraCodecs.optionalAlwaysPresentFieldOf(ExtraCodecs.intRange(1, 99), "count", 1)}，
     * 而 {@code ItemStack.EMPTY} 的 count 为 0，<b>超出 [1,99] 范围</b>，
     * 于是 {@code CODEC.encodeStart(ops, ItemStack.EMPTY)} 直接失败：
     * <pre>Value must be within range [1;99]: 0</pre>
     * 再经 {@code getOrThrow()} 抛出 IllegalStateException。
     *
     * <p>这正是「卸除配件会复制配件」的根因：{@code ClientMessageUnloadAttachment#handle} 先
     * {@code inventory.add(attachmentItem)} 把配件给了玩家，随后
     * {@code unloadAttachment} → {@code saveItemStack(ItemStack.EMPTY)} 抛异常，
     * 枪上的配件 NBT <b>没被清空</b> —— 物品到手、配件还在 = 无限复制。
     *
     * <p>{@code OPTIONAL_CODEC} 对 EMPTY 编码为 {@code {}}，回读得到 {@code ItemStack.EMPTY}，
     * 双向都正确（已实测验证）。
     */
    public static CompoundTag saveItemStack(ItemStack stack) {
        DataResult<Tag> result = ItemStack.OPTIONAL_CODEC.encodeStart(getOps(), stack);
        return (CompoundTag) result.getOrThrow();
    }

    /**
     * Deserialize an ItemStack from a CompoundTag using Codec.
     *
     * <p>与 {@link #saveItemStack} 对称使用 {@code OPTIONAL_CODEC}，
     * 这样空标签 {@code {}} 能正确回读为 {@link ItemStack#EMPTY}。
     */
    public static ItemStack loadItemStack(CompoundTag tag) {
        DataResult<ItemStack> result = ItemStack.OPTIONAL_CODEC.parse(getOps(), tag);
        return result.result().orElse(ItemStack.EMPTY);
    }
}
