package com.tacz.guns.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;

/** Distance text in Minecraft blocks. A pack must not request an unbounded client-world query. */
public final class Rangefinder {
    public static final String MISS = "---";
    @SerializedName("max_distance") private float maxDistance = 512;
    @SerializedName("refresh_tick") private int refreshTick = 2;

    @SerializedName("show_impact_prediction") private boolean showImpactPrediction;
    @SerializedName("impact_prediction_color") private String impactColor = "#FFFFFF";
    @SerializedName("impact_prediction_scale") private float impactScale = 1;

    public boolean showsImpact() { return showImpactPrediction; }
    public int getImpactColor() {
        String rgb = impactColor == null ? "" : impactColor.trim();
        return 0xff000000 | com.tacz.guns.util.ColorHex.colorTextToRbgInt(rgb.length() == 6 ? "#" + rgb : rgb);
    }
    public float getImpactScale() { return Float.isFinite(impactScale) && impactScale > 0 ? Math.min(32, impactScale) : 1; }

    public float getMaxDistance() {
        return !Float.isFinite(maxDistance) || maxDistance < 0 ? 512 : Math.min(maxDistance, 1024);
    }

    public int getRefreshTick() { return refreshTick > 0 ? refreshTick : 2; }

    public String format(double blocks) {
        float limit = getMaxDistance();
        if (!Double.isFinite(blocks) || blocks < 0 || blocks > limit) return MISS;
        String value = Long.toString(Math.round(blocks));
        int width = Integer.toString(Math.round(limit)).length();
        return "0".repeat(Math.max(0, width - value.length())) + value;
    }
}
