package com.tacz.guns.compat.jei.entry;

import com.tacz.guns.api.item.GunTabType;
import com.tacz.guns.compat.recipeviewer.AttachmentQueryData;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Keep the viewer's recipe class stable while sharing its compatibility scan with the reverse view. */
public class AttachmentQueryEntry extends com.tacz.guns.compat.recipeviewer.AttachmentQueryEntry {
    public AttachmentQueryEntry(Identifier id, GunTabType type) { super(id, type); }
    public AttachmentQueryEntry(com.tacz.guns.compat.recipeviewer.AttachmentQueryEntry entry) { super(entry); }

    public static List<AttachmentQueryEntry> getAllAttachmentQueryEntries() {
        return AttachmentQueryData.load().attachmentQueries().stream().map(AttachmentQueryEntry::new).toList();
    }
}
