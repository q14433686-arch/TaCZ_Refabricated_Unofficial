package com.tacz.guns.api.item.nbt;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.util.ItemNbtUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public interface AttachmentItemDataAccessor extends IAttachment {
    String ATTACHMENT_ID_TAG = "AttachmentId";
    String SKIN_ID_TAG = "Skin";
    String ZOOM_NUMBER_TAG = "ZoomNumber";
    String LASER_COLOR_TAG = "LaserColor";

    // 仅检查给定的 CompoundTag 是否具有配件 ID ，不校验其是否存在
    static boolean isAttachmentLike(CompoundTag tag) {
        return tag.contains(ATTACHMENT_ID_TAG);
    }

    @Nonnull
    static Identifier getAttachmentIdFromTag(@Nullable CompoundTag nbt) {
        if (nbt == null) {
            return DefaultAssets.EMPTY_ATTACHMENT_ID;
        }
        if (isAttachmentLike(nbt)) {
            Identifier attachmentId = Identifier.tryParse(nbt.getStringOr(ATTACHMENT_ID_TAG, ""));
            return Objects.requireNonNullElse(attachmentId, DefaultAssets.EMPTY_ATTACHMENT_ID);
        }
        return DefaultAssets.EMPTY_ATTACHMENT_ID;
    }

    static int getZoomNumberFromTag(@Nullable CompoundTag nbt) {
        if (nbt == null) {
            return 0;
        }
        if (nbt.contains(ZOOM_NUMBER_TAG)) {
            return nbt.getIntOr(ZOOM_NUMBER_TAG, 0);
        }
        return 0;
    }

    static void setZoomNumberToTag(CompoundTag nbt, int zoomNumber) {
        nbt.putInt(ZOOM_NUMBER_TAG, zoomNumber);
    }

    @Override
    @Nonnull
    default Identifier getAttachmentId(ItemStack attachmentStack) {
        CompoundTag nbt = ItemNbtUtils.getTag(attachmentStack);
        return getAttachmentIdFromTag(nbt);
    }

    @Override
    default void setAttachmentId(ItemStack attachmentStack, @Nullable Identifier attachmentId) {
        ItemNbtUtils.updateTag(attachmentStack, nbt -> {
            if (attachmentId != null) {
                nbt.putString(ATTACHMENT_ID_TAG, attachmentId.toString());
            }
        });
    }

    @Override
    @Nullable
    default Identifier getSkinId(ItemStack attachmentStack) {
        CompoundTag nbt = ItemNbtUtils.getTag(attachmentStack);
        if (nbt.contains(SKIN_ID_TAG)) {
            return Identifier.tryParse(nbt.getStringOr(SKIN_ID_TAG, ""));
        }
        return null;
    }

    @Override
    default void setSkinId(ItemStack attachmentStack, @Nullable Identifier skinId) {
        ItemNbtUtils.updateTag(attachmentStack, nbt -> {
            if (skinId != null) {
                nbt.putString(SKIN_ID_TAG, skinId.toString());
            } else {
                nbt.remove(SKIN_ID_TAG);
            }
        });
    }

    @Override
    default int getZoomNumber(ItemStack attachmentStack) {
        CompoundTag nbt = ItemNbtUtils.getTag(attachmentStack);
        return getZoomNumberFromTag(nbt);
    }

    @Override
    default void setZoomNumber(ItemStack attachmentStack, int zoomNumber) {
        ItemNbtUtils.updateTag(attachmentStack, nbt -> setZoomNumberToTag(nbt, zoomNumber));
    }

    /**
     * 直接把镭射颜色写进给定的配件 NBT 标签。
     *
     * <p>与 {@link #setLaserColor(ItemStack, int)} 的区别：后者作用于一个
     * {@code ItemStack} 对象，而<b>已安装在枪上的配件并不存在独立的 ItemStack</b> ——
     * 它只是枪 NBT 里的一段数据，{@code IGun#getAttachment} 每次都会用 Codec
     * 反序列化出一个临时副本，改副本不会回写到枪上。
     *
     * <p>因此服务端处理「修改已安装配件的镭射颜色」时必须走
     * {@code getAttachmentTag → setLaserColorToTag → setAttachmentTag} 这条路，
     * 见 {@code ClientMessageLaserColor#handle}。本方法即上游同名静态工具，
     * 移植时遗漏，导致改色无法持久化。
     */
    static void setLaserColorToTag(CompoundTag nbt, int color) {
        nbt.putInt(LASER_COLOR_TAG, color);
    }

    @Override
    default boolean hasCustomLaserColor(ItemStack attachmentStack) {
        CompoundTag nbt = ItemNbtUtils.getTag(attachmentStack);
        return nbt.contains(LASER_COLOR_TAG);
    }

    @Override
    default int getLaserColor(ItemStack attachmentStack) {
        CompoundTag nbt = ItemNbtUtils.getTag(attachmentStack);
        if (!hasCustomLaserColor(attachmentStack)) {
            return 0xFF0000;
        }
        return nbt.getIntOr(LASER_COLOR_TAG, 0xFF0000);
    }

    @Override
    default void setLaserColor(ItemStack attachmentStack, int color) {
        ItemNbtUtils.updateTag(attachmentStack, nbt -> nbt.putInt(LASER_COLOR_TAG, color));
    }
}
