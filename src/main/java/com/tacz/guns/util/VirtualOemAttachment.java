package com.tacz.guns.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class VirtualOemAttachment {
    private static final String VIRTUAL_OEM_TAG = "VirtualOemAttachment";

    private VirtualOemAttachment() {
    }

    public static boolean isAllowedVirtualOem(Identifier gunId, Identifier attachmentId, AttachmentType type) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> {
                    var builtInAttachment = index.getGunData().getBuiltInAttachmentOptions().get(type);
                    return builtInAttachment != null && builtInAttachment.getAttachments().contains(attachmentId);
                })
                .orElse(false);
    }

    public static boolean shouldShowIcon(Identifier gunId, Identifier attachmentId, AttachmentType type) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> {
                    var builtInAttachment = index.getGunData().getBuiltInAttachmentOptions().get(type);
                    // 回退显示的配件已在枪上，不重复列入改装选项。
                    return builtInAttachment != null && builtInAttachment.getAttachments().contains(attachmentId)
                            && builtInAttachment.isShowIcon() && !attachmentId.equals(builtInAttachment.getDisplayAttachment());
                })
                .orElse(false);
    }

    public static boolean isBuiltinAttachment(Identifier attachmentId) {
        return TimelessAPI.getCommonAttachmentIndex(attachmentId)
                .map(index -> index.getPojo().isBuiltinAttachment())
                .orElse(false);
    }

    public static void mark(ItemStack attachmentStack) {
        ItemNbtUtils.updateTag(attachmentStack, tag -> tag.putBoolean(VIRTUAL_OEM_TAG, true));
    }

    public static boolean isMarked(ItemStack attachmentStack) {
        return !attachmentStack.isEmpty() && ItemNbtUtils.getTag(attachmentStack).getBooleanOr(VIRTUAL_OEM_TAG, false);
    }
}
