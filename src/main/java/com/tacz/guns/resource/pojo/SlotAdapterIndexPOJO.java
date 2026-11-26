package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class SlotAdapterIndexPOJO {
    @SerializedName("name")
    private String name;

    @SerializedName("tooltip")
    @Nullable
    private String tooltip;

    @SerializedName("display")
    private Identifier display;

    @SerializedName("data")
    private Identifier data;

    @SerializedName("slot")
    private AttachmentType slot;

    @SerializedName("allowed_attachments")
    private List<String> allowedAttachments = new ArrayList<>();

    @SerializedName("sort")
    private int sort;

    public String getName() {
        return name;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    public Identifier getDisplay() {
        return display;
    }

    public Identifier getData() {
        return data;
    }

    public AttachmentType getSlot() {
        return slot;
    }

    public List<String> getAllowedAttachments() {
        return allowedAttachments == null ? List.of() : allowedAttachments;
    }

    public int getSort() {
        return sort;
    }
}
