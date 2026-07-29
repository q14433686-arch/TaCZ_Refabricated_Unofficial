package com.tacz.guns.client.resource.pojo.display.gun;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;

public class TextShow {
    @SerializedName("scale")
    private float scale = 1.0f;

    @SerializedName("align")
    private Align align = Align.CENTER;

    @SerializedName("shadow")
    private boolean shadow = false;

    @SerializedName("color")
    private String colorText = "#FFFFFF";

    @SerializedName("light")
    private int textLight = 15;

    @SerializedName("text")
    private String textKey = StringUtils.EMPTY;

    /** 默认白色。<b>必须带 alpha</b>，原因见 {@link #setColorInt(int)}。 */
    private volatile int colorInt = 0xFFFFFFFF;

    public float getScale() {
        return scale;
    }

    public Align getAlign() {
        return align;
    }

    public boolean isShadow() {
        return shadow;
    }

    public String getTextKey() {
        return textKey;
    }

    public String getColorText() {
        return colorText;
    }

    public int getTextLight() {
        return textLight;
    }

    public int getColorInt() {
        return colorInt;
    }

    /**
     * 设置文本颜色。<b>会强制补上不透明 alpha。</b>
     *
     * <p>枪包 display json 里写的是 {@code "color": "#FFFFFF"} 这样的<b>六位</b>色值，
     * {@code ColorHex.colorTextToRbgInt} 解析出来自然只有 RGB、alpha 为 0。
     * 1.21.1 的 {@code Font#drawInBatch} 对此宽容；但 26.2 的文本渲染
     * （{@code SubmitNodeCollector#submitText}，与 {@code GuiGraphicsExtractor#text}
     * 同一套判据）遇到 alpha == 0 会<b>直接丢弃整段文字</b>。
     *
     * <p>不补的话，枪身上的文字显示（如 8 倍镜的弹药计数 {@code ammo_count_text}）
     * 会全部看不见。在此处补而不是改 {@code ColorHex}，是因为后者还被
     * {@code colorTextToRbgFloatArray} 使用，那条路径会自行拆分 RGB 分量，
     * 补 alpha 对它没有意义、反而可能引起混淆。
     */
    public void setColorInt(int colorInt) {
        this.colorInt = 0xFF000000 | colorInt;
    }
}
