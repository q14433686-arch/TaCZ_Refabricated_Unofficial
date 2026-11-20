package com.tacz.guns.compat.recipeviewer;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

public class AttachmentQueryEntry {
    public static final int MAX_GUN_SHOW_COUNT = 60;

    private final ItemStack attachmentStack;
    private final List<ItemStack> allowGunStacks;
    private final List<ItemStack> extraAllowGunStacks;

    // Compatibility constructor for the old per-tab wrappers. Registration uses the shared scan.
    public AttachmentQueryEntry(Identifier attachmentId, GunTabType type) {
        this(AttachmentItemBuilder.create().setId(attachmentId).build(), type);
    }

    private AttachmentQueryEntry(ItemStack attachment, GunTabType type) {
        this(attachment, TimelessAPI.getAllCommonGunIndex().stream()
                .filter(e -> type.name().toLowerCase(Locale.ROOT).equals(e.getValue().getType()))
                .map(e -> GunItemBuilder.create().setId(e.getKey()).build())
                .filter(gun -> gun.getItem() instanceof IGun item && item.allowAttachment(gun, attachment)).toList());
    }

    protected AttachmentQueryEntry(AttachmentQueryEntry source) {
        this.attachmentStack = source.attachmentStack;
        this.allowGunStacks = source.allowGunStacks;
        this.extraAllowGunStacks = source.extraAllowGunStacks;
    }

    AttachmentQueryEntry(ItemStack attachmentStack, List<ItemStack> guns) {
        this.attachmentStack = attachmentStack;
        int split = Math.min(guns.size(), MAX_GUN_SHOW_COUNT);
        this.allowGunStacks = List.copyOf(guns.subList(0, split));
        this.extraAllowGunStacks = List.copyOf(guns.subList(split, guns.size()));
    }

    public ItemStack getAttachmentStack() {
        return attachmentStack;
    }

    public List<ItemStack> getAllowGunStacks() {
        return allowGunStacks;
    }

    public List<ItemStack> getExtraAllowGunStacks() {
        return extraAllowGunStacks;
    }
}
