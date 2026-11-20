package com.tacz.guns.util;

import com.google.common.collect.Maps;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;

public final class DefaultGunAttachments {
    private DefaultGunAttachments() {
    }

    public static EnumMap<AttachmentType, Identifier> defaults(Identifier gunId) {
        return TimelessAPI.getCommonGunIndex(gunId)
                .map(index -> {
                    EnumMap<AttachmentType, Identifier> defaults = Maps.newEnumMap(AttachmentType.class);
                    index.getGunData().getBuiltInAttachmentOptions().forEach((type, builtInAttachment) -> {
                        if (builtInAttachment == null) return;
                        Identifier defaultAttachment = builtInAttachment.getDefaultAttachment();
                        if (defaultAttachment != null) {
                            defaults.put(type, defaultAttachment);
                        }
                    });
                    return defaults;
                })
                .orElseGet(() -> Maps.newEnumMap(AttachmentType.class));
    }
}
