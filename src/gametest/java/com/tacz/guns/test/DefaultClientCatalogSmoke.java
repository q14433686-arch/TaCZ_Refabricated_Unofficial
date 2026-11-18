package com.tacz.guns.test;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.ClientIndexManager;
import net.minecraft.resources.Identifier;

final class DefaultClientCatalogSmoke {
    static void client() {
        DefaultCatalogSmoke.catalog(TimelessAPI.getAllClientGunIndex(), 70, "client gun");
        DefaultCatalogSmoke.catalog(TimelessAPI.getAllClientAmmoIndex(), 25, "client ammo");
        DefaultCatalogSmoke.catalog(TimelessAPI.getAllClientAttachmentIndex(), 126, "client attachment");
        DefaultCatalogSmoke.catalog(ClientIndexManager.getAllBlocks(), 3, "client block");
        DefaultCatalogSmoke.catalog(ClientIndexManager.SLOT_ADAPTER_INDEX.entrySet(), 5, "client adapter");
        for (var entry : TimelessAPI.getAllClientGunIndex()) {
            if (!entry.getKey().getNamespace().equals("tacz")) continue;
            var display = entry.getValue().getDefaultDisplay();
            if (display == null || display.getAnimationStateMachine() == null || display.getGunModel() == null)
                throw new AssertionError("Unusable default model or animation: " + entry.getKey());
        }
        var display = TimelessAPI.getClientGunIndex(Identifier.parse("tacz:m1897")).orElseThrow().getDefaultDisplay();
        if (display.getShellModel() == null || !Identifier.parse("tacz:textures/shell/12g_copper_shell.png").equals(display.getShellTextureLocation()))
            throw new AssertionError("Gun-specific shell was replaced by the generic ammo shell");
        GunMod.LOGGER.info("PORT_CATALOG: client loaded 229 indexes, all 70 first-person rigs/state machines and the shell override");
    }
}
