package com.tacz.guns.resource.pojo.data.gun;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Three distinct roles: installable OEM options, the initial stack, and the empty-slot visual. */
public record BuiltInAttachment(List<Identifier> getAttachments, @Nullable Identifier getDefaultAttachment,
                                @Nullable Identifier getDisplayAttachment, boolean isShowIcon) {
    public BuiltInAttachment {
        getAttachments = List.copyOf(getAttachments);
    }
}
