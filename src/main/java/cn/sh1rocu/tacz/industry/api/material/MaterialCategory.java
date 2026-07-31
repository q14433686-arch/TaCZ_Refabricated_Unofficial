package cn.sh1rocu.tacz.industry.api.material;

import cn.sh1rocu.tacz.industry.api.internal.IndustryCodecs;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Nullable;

/**
 * 材料类别（A-1 材料树三层抽象）。
 *
 * <p>材料树的纵向结构：矿 → 冶金中间品 → 化学中间品 →（零件/成品由各工艺章节落地）。
 * 本枚举只表达"这一层属于哪一大类"，tier（T0–T5 科技阶段）是 MaterialType 上的独立字段。</p>
 */
public enum MaterialCategory {
    /** 矿层：原始采掘物（T0，多为原版物品的镜像语义） */
    ORE("ore"),
    /** 冶金中间品：锭、坯、板等（T1–T3） */
    METALLURGY("metallurgy"),
    /** 化学中间品：粉、酸（抽象流体）、发射药基料等（T2/T4） */
    CHEMICAL("chemical");

    public static final Codec<MaterialCategory> CODEC =
            IndustryCodecs.enumByName(MaterialCategory.class, values(), MaterialCategory::getSerializedName);

    private final String serializedName;

    MaterialCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }

    /** JSON 解析包容写法：大小写/连字符不敏感；非法值返回 null 由调用方按"坏单条跳过"处理。 */
    @Nullable
    public static MaterialCategory fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String key = raw.trim().toLowerCase().replace('-', '_');
        for (MaterialCategory c : values()) {
            if (c.serializedName.equals(key)) {
                return c;
            }
        }
        return null;
    }
}
