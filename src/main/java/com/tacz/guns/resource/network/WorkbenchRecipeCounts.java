package com.tacz.guns.resource.network;

import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;

import java.util.Set;

/** Diagnostic path counts, not pack provenance or selection rules. A pack can contribute to another namespace. */
public record WorkbenchRecipeCounts(int total, int gun, int attachments, int ammo) {
    public static WorkbenchRecipeCounts inTaczNamespace(Set<Identifier> ids) {
        int total = 0, gun = 0, attachments = 0, ammo = 0;
        for (var id : ids) {
            if (!GunMod.MOD_ID.equals(id.getNamespace())) continue;
            total++;
            String path = id.getPath();
            if (path.startsWith("gun/")) gun++;
            else if (path.startsWith("attachments/")) attachments++;
            else if (path.startsWith("ammo/")) ammo++;
        }
        return new WorkbenchRecipeCounts(total, gun, attachments, ammo);
    }

    @Override public String toString() {
        return total + " (gun=" + gun + ", attachments=" + attachments + ", ammo=" + ammo + ")";
    }
}
