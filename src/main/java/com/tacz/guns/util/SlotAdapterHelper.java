package com.tacz.guns.util;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonSlotAdapterIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SlotAdapterHelper {
    private static final String SLOT_ADAPTERS_TAG = "SlotAdapters";
    private static final String TAG_PREFIX = "#";
    private static final String ADAPTER_TAG_PATH_PREFIX = "adapter/";

    private SlotAdapterHelper() {
    }

    public static void setSlotAdapter(ItemStack holder, AttachmentType type, Identifier adapterId) {
        if (holder.isEmpty() || type == null || adapterId == null) {
            return;
        }
        ItemNbtUtils.updateTag(holder, root -> {
            CompoundTag adapters = root.getCompoundOrEmpty(SLOT_ADAPTERS_TAG);
            adapters.putString(slotKey(type), adapterId.toString());
            root.put(SLOT_ADAPTERS_TAG, adapters);
        });
    }

    public static @Nullable Identifier getSlotAdapter(ItemStack holder, AttachmentType type) {
        if (holder.isEmpty() || type == null) return null;
        return ItemNbtUtils.getTag(holder).getCompound(SLOT_ADAPTERS_TAG)
                .flatMap(tag -> tag.getString(slotKey(type))).map(Identifier::tryParse).orElse(null);
    }

    public static void removeSlotAdapter(ItemStack holder, AttachmentType type) {
        if (holder.isEmpty() || type == null) return;
        ItemNbtUtils.updateTag(holder, root -> root.getCompound(SLOT_ADAPTERS_TAG).ifPresent(adapters -> {
            adapters.remove(slotKey(type));
            if (adapters.isEmpty()) root.remove(SLOT_ADAPTERS_TAG);
            else root.put(SLOT_ADAPTERS_TAG, adapters);
        }));
    }

    public static boolean allowsDirectAttachment(ItemStack gun, Identifier attachmentId) {
        Identifier hostGunId = getHostGunId(gun);
        return hostGunId != null && attachmentId != null && AllowAttachmentTagMatcher.match(hostGunId, attachmentId);
    }

    public static List<Map.Entry<Identifier, CommonSlotAdapterIndex>> getAvailableSlotAdapters(ItemStack gun, AttachmentType attachmentType) {
        Identifier hostGunId = getHostGunId(gun);
        if (hostGunId == null || attachmentType == null || attachmentType == AttachmentType.NONE) {
            return List.of();
        }
        Set<String> allowList = CommonAssetsManager.get().getAllowAttachmentTags(hostGunId);
        if (allowList == null || allowList.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<Identifier, CommonSlotAdapterIndex> candidates = new LinkedHashMap<>();
        Set<String> roots = new LinkedHashSet<>();
        for (String value : allowList) {
            Identifier tagId = parseTag(value);
            if (tagId == null || !tagId.getPath().startsWith(ADAPTER_TAG_PATH_PREFIX)) {
                continue;
            }
            roots.add(value);
        }
        for (Identifier candidate : AllowAttachmentTagMatcher.expand(roots, null, CommonAssetsManager.get()::getAttachmentTags)) {
            TimelessAPI.getCommonSlotAdapterIndex(candidate).filter(index -> index.getSlot() == attachmentType)
                    .ifPresent(index -> candidates.put(candidate, index));
        }
        return candidates.entrySet().stream().map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList();
    }

    public static boolean isSlotAdapterAvailable(ItemStack gun, AttachmentType attachmentType, Identifier adapterId) {
        if (adapterId == null) {
            return false;
        }
        return getAvailableSlotAdapters(gun, attachmentType).stream().anyMatch(entry -> entry.getKey().equals(adapterId));
    }

    @Nullable
    public static Identifier getSelectedAvailableSlotAdapter(ItemStack gun, AttachmentType attachmentType) {
        if (TimelessAPI.getAllCommonSlotAdapterIndex().isEmpty()) return null;
        Identifier selected = getSlotAdapter(gun, attachmentType);
        if (selected == null) {
            return null;
        }
        return isSlotAdapterAvailable(gun, attachmentType, selected) ? selected : null;
    }

    public static boolean allowsAttachment(ItemStack gun, AttachmentType attachmentType, Identifier attachmentId) {
        if (attachmentType == null || attachmentType == AttachmentType.NONE || attachmentId == null) {
            return false;
        }
        Identifier selected = getSelectedAvailableSlotAdapter(gun, attachmentType);
        if (selected == null) {
            return allowsDirectAttachment(gun, attachmentId);
        }
        return TimelessAPI.getCommonSlotAdapterIndex(selected)
                .map(index -> index.allowsAttachment(attachmentId))
                .orElse(false);
    }

    @Nullable
    public static Identifier getEffectiveSlotAdapter(ItemStack gun, AttachmentType attachmentType, Identifier attachmentId) {
        Identifier selected = getSelectedAvailableSlotAdapter(gun, attachmentType);
        if (selected == null || attachmentId == null) {
            return null;
        }
        return TimelessAPI.getCommonSlotAdapterIndex(selected)
                .filter(index -> index.allowsAttachment(attachmentId))
                .map(index -> selected)
                .orElse(null);
    }

    public static boolean allowsSlotAdapters(ItemStack holder) {
        return getHostGunId(holder) != null;
    }

    @Nullable
    private static Identifier parseTag(String value) {
        if (value == null || !value.startsWith(TAG_PREFIX)) {
            return null;
        }
        return Identifier.tryParse(value.substring(TAG_PREFIX.length()));
    }

    private static String slotKey(AttachmentType type) {
        return type.name();
    }

    @Nullable
    private static Identifier getHostGunId(ItemStack holder) {
        IGun gun = IGun.getIGunOrNull(holder);
        return gun == null ? null : gun.getGunId(holder);
    }
}
