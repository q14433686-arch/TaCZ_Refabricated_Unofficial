package com.tacz.guns.resource.manager;

import com.google.gson.JsonElement;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.network.DataType;
import net.minecraft.resources.Identifier;

public class GunIndexManager extends CommonDataManager<CommonGunIndex> {
    public GunIndexManager() {
        super(DataType.GUN_INDEX, CommonGunIndex.class, CommonAssetsManager.GSON, "index/guns", "GunIndexLoader");
    }

    @Override
    protected String toNetwork(Identifier id, JsonElement element) {
        var index = getData(id);
        if (!element.isJsonObject()) return super.toNetwork(id, element);
        // Never sync logic scripts. Disregard a pack's self-reported capability on the server.
        var synced = element.getAsJsonObject().deepCopy();
        synced.addProperty("has_unload_ammo_script", index != null && index.hasUnloadAmmoScript());
        return synced.toString();
    }
}
