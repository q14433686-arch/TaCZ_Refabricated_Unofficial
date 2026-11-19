package com.tacz.guns.resource.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.Identifier;

public class GunDataManager extends CommonDataManager<GunData> {
    private static final String EXCLUSIVE_ATTACHMENTS = "exclusive_attachments";

    public GunDataManager() {
        super(DataType.GUN_DATA, GunData.class, CommonAssetsManager.GSON, "data/guns", "GunDataLoader");
    }

    @Override
    protected GunData parseJson(JsonElement element) {
        return parseData(element);
    }

    public static GunData parseData(JsonElement element) {
        GunData data = CommonAssetsManager.GSON.fromJson(element, GunData.class);
        initExclusiveAttachmentModifiers(data, element);
        return data;
    }

    public static void initExclusiveAttachmentModifiers(GunData data, JsonElement element) {
        if (data == null || !element.isJsonObject()) {
            return;
        }
        JsonElement exclusiveElement = element.getAsJsonObject().get(EXCLUSIVE_ATTACHMENTS);
        if (exclusiveElement == null || !exclusiveElement.isJsonObject()) {
            return;
        }
        JsonObject exclusiveAttachments = exclusiveElement.getAsJsonObject();
        exclusiveAttachments.entrySet().forEach(entry -> {
            Identifier attachmentId = Identifier.tryParse(entry.getKey());
            if (attachmentId == null) {
                return;
            }
            var attachmentData = data.getExclusiveAttachments().get(attachmentId);
            if (attachmentData != null) {
                AttachmentDataManager.initModifiers(attachmentData, entry.getValue(), CommonAssetsManager.GSON);
            }
        });
    }
}
