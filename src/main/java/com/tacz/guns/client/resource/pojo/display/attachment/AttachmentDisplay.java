package com.tacz.guns.client.resource.pojo.display.attachment;

import org.joml.Vector3f;
import com.google.common.collect.Maps;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.client.resource.pojo.display.IDisplay;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.client.resource.pojo.display.gun.TextShow;
import net.minecraft.resources.Identifier;

import javax.annotation.Nullable;
import java.util.Map;

public class AttachmentDisplay implements IDisplay {
    @SerializedName("laser")
    private LaserConfig laserConfig;

    @SerializedName("slot")
    private Identifier slotTextureLocation;

    @SerializedName("model")
    private Identifier model;

    @SerializedName("texture")
    private Identifier texture;

    @SerializedName("lod")
    @Nullable
    private AttachmentLod attachmentLod;

    @SerializedName("adapter")
    @Nullable
    private String adapterNodeName;

    @SerializedName("show_muzzle")
    private boolean showMuzzle = false;

    /** 枪口火光相对枪口锚点的纵向偏移（donor 26.1.2 {@code muzzle_flash_offset}）。 */
    @SerializedName("muzzle_flash_offset")
    private float muzzleFlashOffset;

    public float getMuzzleFlashOffset() {
        return Float.isFinite(muzzleFlashOffset) && muzzleFlashOffset >= 0 ? muzzleFlashOffset : 0;
    }

    @SerializedName("show_mount")
    private boolean showMount = true;

    @SerializedName("text_show")
    private Map<String, TextShow> textShows = Maps.newHashMap();

    @SerializedName("zoom")
    @Nullable
    private float[] zoom;

    @SerializedName("views")
    @Nullable
    private int[] views;

    @SerializedName("scope_lens")
    private ScopeLensConfig scopeLens = new ScopeLensConfig();

    public ScopeLensConfig getScopeLens() { return scopeLens; }

    @SerializedName("scope")
    private boolean isScope = false;

    @SerializedName("sight")
    private boolean isSight = false;

    @SerializedName("idle_scope_height_adjust")
    private float idleScopeHeightAdjust;

    public float getIdleScopeHeightAdjust() {
        return Float.isFinite(idleScopeHeightAdjust) ? idleScopeHeightAdjust : 0;
    }

    @SerializedName("fov")
    private float fov = 70;

    @SerializedName("views_fov")
    @Nullable
    private float[] viewsFov;

    @SerializedName("mode")
    private String variantMode = "replace";
    @SerializedName("mount_offset")
    private @Nullable Vector3f mountOffset;

    public String getVariantMode() { return variantMode; }
    public @Nullable Vector3f getMountOffset() { return mountOffset != null && mountOffset.isFinite() ? mountOffset : null; }

    @SerializedName("sounds")
    private Map<String, Identifier> sounds = Maps.newHashMap();

    public Identifier getSlotTextureLocation() {
        return slotTextureLocation;
    }

    public Identifier getModel() {
        return model;
    }

    public Identifier getTexture() {
        return texture;
    }

    @Nullable
    public AttachmentLod getAttachmentLod() {
        return attachmentLod;
    }

    @Nullable
    public String getAdapterNodeName() {
        return adapterNodeName;
    }

    public boolean isShowMuzzle() {
        return showMuzzle;
    }

    public boolean isShowMount() {
        return showMount;
    }

    public Map<String, TextShow> getTextShows() {
        return textShows;
    }

    @Nullable
    public float[] getZoom() {
        return zoom;
    }

    @Nullable
    public int[] getViews() {
        return views;
    }

    public boolean isScope() {
        return isScope;
    }

    public boolean isSight() {
        return isSight;
    }

    public float getFov() {
        return fov;
    }

    @Nullable
    public float[] getViewsFov() {
        return viewsFov;
    }

    public Map<String, Identifier> getSounds() {
        return sounds;
    }

    @Nullable
    public LaserConfig getLaserConfig() {
        return laserConfig;
    }

    @Override
    public void init() {
        if (scopeLens == null) scopeLens = new ScopeLensConfig();
        scopeLens.init();
        if (slotTextureLocation != null) {
            slotTextureLocation = converter.idToFile(slotTextureLocation);
        }
        if (texture != null) {
            texture = converter.idToFile(texture);
        }
        if (attachmentLod != null && attachmentLod.modelTexture != null) {
            attachmentLod.modelTexture = converter.idToFile(attachmentLod.modelTexture);
        }
    }
}
