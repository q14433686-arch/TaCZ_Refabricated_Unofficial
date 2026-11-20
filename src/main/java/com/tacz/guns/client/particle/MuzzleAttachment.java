package com.tacz.guns.client.particle;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.custom.SilenceModifier;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Client visual properties of the part actually fitted to this stack. An exclusive entry
 * replaces, rather than merges with, common data. A built-in is used only for an empty slot. */
public record MuzzleAttachment(@Nullable AttachmentData data, float offsetBb) {
    private static final MuzzleAttachment NONE = new MuzzleAttachment(null, 0);

    public static MuzzleAttachment of(ItemStack stack) {
        var gun = IGun.getIGunOrNull(stack);
        if (gun == null) return NONE;
        var mounted = gun.getAttachmentId(stack, AttachmentType.MUZZLE);
        var id = DefaultAssets.isEmptyAttachmentId(mounted) ? gun.getBuiltInAttachmentId(stack, AttachmentType.MUZZLE) : mounted;
        if (DefaultAssets.isEmptyAttachmentId(id)) return NONE;
        var data = TimelessAPI.getCommonGunIndex(gun.getGunId(stack))
                .map(index -> index.getGunData().getExclusiveAttachments().get(id))
                .or(() -> TimelessAPI.getCommonAttachmentIndex(id).map(index -> index.getData())).orElse(null);
        float offset = TimelessAPI.getClientAttachmentIndex(id).map(index -> index.getMuzzleFlashOffset()).orElse(0f);
        return new MuzzleAttachment(data, offset);
    }

    public boolean silenced() {
        var modifier = data == null ? null : data.getModifier().get(SilenceModifier.ID);
        return modifier != null && modifier.getValue() instanceof Pair<?, ?> pair && Boolean.TRUE.equals(pair.right());
    }
}
