package com.tacz.guns.client.resource.index;

import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.resource.pojo.SlotAdapterIndexPOJO;
import org.jetbrains.annotations.Nullable;

public final class ClientSlotAdapterIndex extends RefitOptionModel {
    public ClientSlotAdapterIndex(SlotAdapterIndexPOJO pojo) {
        super(pojo.getName(), ClientAssetsManager.INSTANCE.getSlotAdapterDisplay(pojo.getDisplay()));
    }

    public @Nullable BedrockAttachmentModel getAdapterModel() { return getModel(); }
}
