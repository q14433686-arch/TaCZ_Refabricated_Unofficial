package com.tacz.guns.resource.pojo;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class AttachmentVariantIndexPOJO {
    @SerializedName("name")
    private String name;

    @SerializedName("tooltip")
    @Nullable
    private String tooltip;

    @SerializedName("display")
    private Identifier display;

    @SerializedName("data")
    private Identifier data;

    @SerializedName("targets")
    private List<Identifier> targets = new ArrayList<>();

    @SerializedName("target_tags")
    private List<Identifier> targetTags = new ArrayList<>();

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

    public List<Identifier> getTargets() {
        return targets == null ? List.of() : targets;
    }

    public List<Identifier> getTargetTags() {
        return targetTags == null ? List.of() : targetTags;
    }

    public int getSort() {
        return sort;
    }
}
