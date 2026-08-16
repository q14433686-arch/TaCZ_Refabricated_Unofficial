package me.xjqsh.lrtactical.item.melee;

import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.api.melee.MeleeAction;
import me.xjqsh.lrtactical.util.TooltipLine;
import me.xjqsh.lrtactical.util.TooltipUtil;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 一把近战武器的完整配置，对应数据包 {@code index/melee/<name>.json} 的 {@code data} 段。
 *
 * <p>纯 Gson POJO，与上游逐字对应。
 */
public class MeleeWeaponData {
    /** 切入（举起）时间，tick。 */
    @SerializedName("draw_time")
    private int drawTime;

    /** 收起时间，tick。 */
    @SerializedName("put_away_time")
    private int putAwayTime;

    @SerializedName("attack")
    private CombatData attackInfo = new CombatData();

    @SerializedName("attributes")
    private AttributeData attributes = new AttributeData();

    /** 0 表示不可损坏。 */
    @SerializedName("max_durability")
    private int maxDurability = 0;

    @SerializedName("enchantment_value")
    private int enchantmentValue = 14;

    @SerializedName("tool")
    private MeleeToolData tool = new MeleeToolData();

    public int getDrawTime() {
        return drawTime;
    }

    public int getPutAwayTime() {
        return putAwayTime;
    }

    public CombatData getAttackInfo() {
        return attackInfo;
    }

    public AttributeData getRawAttributes() {
        return attributes;
    }

    public int getMaxDurability() {
        return maxDurability;
    }

    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    public MeleeToolData getTool() {
        return tool == null ? new MeleeToolData() : tool;
    }

    public List<TooltipLine> getTooltipLines() {
        List<TooltipLine> lines = new ArrayList<>();
        for (MeleeAction action : MeleeAction.values()) {
            CombatData.MeleeAttackInfo info = attackInfo == null
                    ? null : attackInfo.getAttackInfo(action);
            if (info == null) {
                continue;
            }
            String actionKey = action == MeleeAction.LEFT
                    ? "tooltip.lrtactical.melee.attack_left"
                    : "tooltip.lrtactical.melee.attack_right";
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.melee.action_line", Component.translatable(actionKey),
                    TooltipUtil.formatFactor(info.getFactor()),
                    TooltipUtil.formatTicks(info.getCooldown()))));
        }
        return lines;
    }
}
