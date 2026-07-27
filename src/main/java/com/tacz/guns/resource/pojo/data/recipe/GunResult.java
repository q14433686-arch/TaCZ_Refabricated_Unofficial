package com.tacz.guns.resource.pojo.data.recipe;

import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;

public class GunResult {
    @SerializedName("ammo_count")
    private int ammoCount = 0;

    @SerializedName("attachments")
    private EnumMap<AttachmentType, Identifier> attachments = Maps.newEnumMap(AttachmentType.class);

    public GunResult() {
    }

    public GunResult(int ammoCount, EnumMap<AttachmentType, Identifier> attachments) {
        this.ammoCount = Math.max(0, ammoCount);
        this.attachments = attachments == null ? Maps.newEnumMap(AttachmentType.class) : attachments;
    }

    public int getAmmoCount() {
        return ammoCount;
    }

    public EnumMap<AttachmentType, Identifier> getAttachments() {
        return attachments;
    }
}
