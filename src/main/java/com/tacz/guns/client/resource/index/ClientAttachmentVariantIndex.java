package com.tacz.guns.client.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.resource.pojo.AttachmentVariantIndexPOJO;
import org.jetbrains.annotations.Nullable;

public final class ClientAttachmentVariantIndex extends RefitOptionModel {
    private final float[] fov;
    private final int[] views;

    public ClientAttachmentVariantIndex(AttachmentVariantIndexPOJO pojo) {
        super(pojo.getName(), ClientAssetsManager.INSTANCE.getAttachmentVariantDisplay(pojo.getDisplay()));
        var rawFov = display.getViewsFov();
        fov = rawFov == null || rawFov.length == 0 ? new float[]{display.getFov()} : rawFov;
        for (float value : fov) Preconditions.checkArgument(value > 0 && value < 180, "Invalid variant FOV");
        var rawViews = display.getViews();
        views = rawViews == null || rawViews.length == 0 ? new int[]{1} : rawViews;
        for (int view : views) Preconditions.checkArgument(view >= 1, "Invalid variant view group");
        if (getZoom() != null) for (float z : getZoom()) Preconditions.checkArgument(Float.isFinite(z) && z >= 1, "Invalid variant zoom");
    }

    public boolean isOverlayMode() { return "overlay".equalsIgnoreCase(display.getVariantMode()); }
    public boolean isReplaceMode() { return !isOverlayMode(); }
    public float @Nullable [] getZoom() { return display.getZoom(); }
    public float[] getViewsFov() { return fov; }
    public int[] getViews() { return views; }
    public boolean isScope() { return display.isScope(); }
    public boolean isSight() { return display.isSight(); }
    public @Nullable BedrockAttachmentModel getAttachmentModel() { return getModel(); }
}
