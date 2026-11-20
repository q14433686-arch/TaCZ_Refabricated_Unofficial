package com.tacz.guns.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.resource.ICommonResourceProvider;
import com.tacz.guns.resource.pojo.AttachmentVariantIndexPOJO;
import com.tacz.guns.resource.pojo.data.attachment.VariantData;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Objects;

public class CommonAttachmentVariantIndex {
    private VariantData data;
    private AttachmentVariantIndexPOJO pojo;
    private int sort;
    private List<Identifier> targets;
    private List<Identifier> targetTags;

    private CommonAttachmentVariantIndex() {
    }

    public static CommonAttachmentVariantIndex getInstance(AttachmentVariantIndexPOJO pojo, ICommonResourceProvider resources) throws IllegalArgumentException {
        CommonAttachmentVariantIndex index = new CommonAttachmentVariantIndex();
        index.pojo = pojo;
        checkIndex(pojo, index);
        checkData(pojo, index, resources);
        return index;
    }

    private static void checkIndex(AttachmentVariantIndexPOJO pojo, CommonAttachmentVariantIndex index) {
        Preconditions.checkArgument(pojo != null, "index object file is empty");
        Preconditions.checkArgument(pojo.getDisplay() != null, "attachment variant display must be nonnull.");
        index.sort = Mth.clamp(pojo.getSort(), 0, 65536);
        Preconditions.checkArgument(pojo.getTargets().stream().noneMatch(Objects::isNull) && pojo.getTargetTags().stream().noneMatch(Objects::isNull), "Invalid variant target(s)");
        index.targets = List.copyOf(pojo.getTargets());
        index.targetTags = List.copyOf(pojo.getTargetTags());
    }

    private static void checkData(AttachmentVariantIndexPOJO pojo, CommonAttachmentVariantIndex index, ICommonResourceProvider resources) {
        Identifier dataId = pojo.getData();
        Preconditions.checkArgument(dataId != null, "index object missing pojoData field");
        VariantData data = resources.getAttachmentVariantData(dataId);
        Preconditions.checkArgument(data != null, "there is no corresponding data file");
        index.data = data;
    }

    public VariantData getData() {
        return data;
    }

    public AttachmentVariantIndexPOJO getPojo() {
        return pojo;
    }

    public int getSort() {
        return sort;
    }

    public List<Identifier> getTargets() {
        return targets;
    }

    public List<Identifier> getTargetTags() {
        return targetTags;
    }

    public boolean appliesTo(Identifier attachmentId) {
        if (attachmentId == null) {
            return false;
        }
        if (targets.contains(attachmentId)) {
            return true;
        }
        return targetTags.stream().anyMatch(tag -> AllowAttachmentTagMatcher.matchTag(tag, attachmentId));
    }
}
