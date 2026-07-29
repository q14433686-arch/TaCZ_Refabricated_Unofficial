package me.xjqsh.lrtactical.item.melee;

import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.EquipmentMod;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 可选的近战物品挖掘能力配置。
 *
 * <p>LRTactical 上游并未真正适配 Forge 的 ToolAction/工具挖掘能力；本移植不能把
 * 「刀」统一写死成 axe/shovel，因为真实刀包里可能同时出现匕首、工兵铲、消防斧等完全不同的物件。
 * 因此这里采用数据驱动：默认近战武器不作为工具挖掘；内容包显式声明 tag 后才获得对应挖掘能力。</p>
 *
 * <pre>{@code
 * "tool": {
 *   "mineable_tags": ["minecraft:mineable/axe"],
 *   "speed": 6.0,
 *   "damage_per_block": 1,
 *   "correct_for_drops": true
 * }
 * }</pre>
 */
public class MeleeToolData {
    /** 为空/缺省表示“不是工具”：挖掘速度为 0，不匹配任何可采掘 tag。 */
    @SerializedName("mineable_tags")
    private List<String> mineableTags = List.of();

    @SerializedName("speed")
    private float speed = 1.0F;

    @SerializedName("damage_per_block")
    private int damagePerBlock = 1;

    @SerializedName("correct_for_drops")
    private boolean correctForDrops = false;

    public boolean hasRules() {
        return mineableTags != null && !mineableTags.isEmpty();
    }

    /**
     * 给物品栈写入 26.2 的 {@link DataComponents#TOOL} 组件。
     *
     * <p>即使没有规则也写入一个 defaultMiningSpeed=0 的 Tool：这样未显式配置的匕首/刀不会
     * 像空手一样慢慢挖方块，避免“所有近战都能当万能工具”的误判。消防斧、工兵铲等由数据包声明
     * {@code mineable_tags} 后获得实际挖掘能力。</p>
     */
    public void applyTo(ItemStack stack) {
        List<Tool.Rule> rules = new ArrayList<>();
        if (mineableTags != null) {
            for (String raw : mineableTags) {
                Identifier id = parseTagId(raw);
                if (id == null) {
                    EquipmentMod.LOGGER.warn("Invalid melee tool block tag id: {}", raw);
                    continue;
                }
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
                HolderSet.Named<Block> blocks = HolderSet.emptyNamed(BuiltInRegistries.BLOCK, tag);
                rules.add(new Tool.Rule(blocks, Optional.of(Math.max(0.0F, speed)), Optional.of(correctForDrops)));
            }
        }
        stack.set(DataComponents.TOOL, new Tool(rules, 0.0F, Math.max(0, damagePerBlock), true));
    }

    private static Identifier parseTagId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.charAt(0) == '#' ? raw.substring(1) : raw;
        return Identifier.tryParse(value);
    }
}
