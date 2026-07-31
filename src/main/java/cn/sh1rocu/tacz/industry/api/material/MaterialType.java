package cn.sh1rocu.tacz.industry.api.material;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 材料类型（A-1 材料树的节点定义，数据驱动注册表条目）。
 *
 * <p><b>三元数据（A-1 设计要求）：</b></p>
 * <ul>
 *   <li>{@code tier} 科技阶段 0–5（T0 矿 → T5 重度自动化产品级材料）</li>
 *   <li>{@code workTags} 可加工列表（自由标签：forgeable/castable/machinable_low/pressable…，
 *       工艺机器在规则层按标签判定准入，新增工艺 = 新标签，零代码扩展）</li>
 *   <li>{@code toleranceBonus} 公差加成（A-8b 表的材料分项，直接进入 TS 公式）</li>
 * </ul>
 *
 * <p>其余字段：{@code upstream} 记录直接上游材料（材料树的边，供配方推导与手册展示）；
 * {@code itemHint} 预留"将来物品化时对应的物品 id"（物品层后置期间允许悬空引用）。</p>
 */
public record MaterialType(
        Identifier id,
        MaterialCategory category,
        int tier,
        float toleranceBonus,
        Set<String> workTags,
        List<Identifier> upstream,
        @Nullable Identifier itemHint
) {
    /** 从数据包 JSON 解析（IndustryDataLoader 调用；坏 JSON 在 loader 层单条跳过）。 */
    public static MaterialType fromJson(Identifier id, JsonObject json) {
        MaterialCategory category = MaterialCategory.fromString(GsonHelper.getAsString(json, "category", "metallurgy"));
        if (category == null) {
            throw new IllegalArgumentException("material " + id + " 的 category 非法: "
                    + GsonHelper.getAsString(json, "category") + "（允许 ore/metallurgy/chemical）");
        }
        int tier = GsonHelper.getAsInt(json, "tier", 1);
        if (tier < 0 || tier > 5) {
            throw new IllegalArgumentException("material " + id + " 的 tier 必须在 0-5: " + tier);
        }
        float bonus = GsonHelper.getAsFloat(json, "tolerance_bonus", 0f);
        Set<String> tags = new LinkedHashSet<>();
        if (json.has("work_tags")) {
            GsonHelper.getAsJsonArray(json, "work_tags").forEach(e -> tags.add(e.getAsString()));
        }
        List<Identifier> upstream = new ArrayList<>();
        if (json.has("upstream")) {
            GsonHelper.getAsJsonArray(json, "upstream").forEach(e -> {
                Identifier up = Identifier.tryParse(e.getAsString());
                if (up != null) {
                    upstream.add(up);
                }
            });
        }
        Identifier hint = json.has("item_hint") ? Identifier.tryParse(GsonHelper.getAsString(json, "item_hint")) : null;
        return new MaterialType(id, category, tier, bonus, Set.copyOf(tags), List.copyOf(upstream), hint);
    }

    /** 可加工性检查（规则层与手册共用入口）。 */
    public boolean workable(String tag) {
        return workTags.contains(tag);
    }
}
