package com.tacz.guns.resource.manager;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tacz.guns.api.modifier.JsonProperty;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.network.DataType;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;


public class AttachmentDataManager extends CommonDataManager<AttachmentData> {

    public AttachmentDataManager() {
        super(DataType.ATTACHMENT_DATA, AttachmentData.class, CommonAssetsManager.GSON, "data/attachments", "AttachmentDataLoader");
    }

    @Override
    protected AttachmentData parseJson(JsonElement element) {
        return parseData(element, getGson());
    }

    public static AttachmentData parseData(JsonElement element, Gson gson) {
        var data = gson.fromJson(element, AttachmentData.class);
        initModifiers(data, element, gson);
        return data;
    }

    public static void initModifiers(AttachmentData data, JsonElement element, Gson gson) {
        if (data == null || !element.isJsonObject()) return;
        JsonObject fields = element.getAsJsonObject();
        String json = gson.toJson(element);
        AttachmentPropertyManager.getModifiers().forEach((key, modifier) -> {
            if (fields.has(key) || modifier.getOptionalFieldList().stream().anyMatch(fields::has)) {
                JsonProperty<?> property = modifier.readJson(json);
                property.initComponents();
                data.addModifier(key, property);
            }
        });
    }
}
