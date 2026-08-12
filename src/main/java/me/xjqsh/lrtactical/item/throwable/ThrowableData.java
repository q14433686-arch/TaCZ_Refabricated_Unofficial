package me.xjqsh.lrtactical.item.throwable;

import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.util.TooltipLine;
import me.xjqsh.lrtactical.util.TooltipUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 投掷物的配置，由数据包 {@code index/throwable/*.json} 的 {@code data} 段驱动。
 *
 * <p>26.2 变更：{@code ResourceLocation} → {@link Identifier}（全仓统一）。
 * 其余字段与上游逐一对应。
 *
 * <p>子类（爆炸雷 / 烟雾弹 / 闪光弹 / 效果云）会在此基础上追加各自的字段。
 */
public class ThrowableData {
    @SerializedName("prepare_time")
    private int prepareTime = 10;

    @SerializedName("cookable")
    private boolean cookable = false;

    @SerializedName("initial_speed")
    private double initialSpeed = 1.5;

    @SerializedName("cooldown")
    private int cooldown = 40;

    @SerializedName("cooldown_category")
    private Identifier cooldownCategory = null;

    @SerializedName("stack_size")
    private int stackSize = 1;

    @SerializedName("entity")
    private EntityData entityData = new EntityData();

    @SerializedName("put_away_time")
    private long putAwayTime = 0;

    public int getPrepareTime() {
        return prepareTime;
    }

    public double getInitialSpeed() {
        return initialSpeed;
    }

    public int getCooldown() {
        return cooldown;
    }

    @Nullable
    public Identifier getCooldownCategory() {
        return cooldownCategory;
    }

    public int getStackSize() {
        return stackSize;
    }

    public EntityData getEntityData() {
        return entityData;
    }

    public long getPutAwayTime() {
        return putAwayTime;
    }

    public boolean isCookable() {
        return cookable;
    }

    /** Common tooltip lines; subtype data appends its own values. */
    public List<TooltipLine> getTooltipLines() {
        List<TooltipLine> lines = new ArrayList<>();
        if (getCooldown() > 0) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.throwable.cooldown", TooltipUtil.formatTicks(getCooldown()))));
        }
        if (isCookable()) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.throwable.cookable")));
        }
        if (getClass() == ThrowableData.class && getEntityData().getLifeTime() > 0) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.throwable.life_time",
                    TooltipUtil.formatTicks(getEntityData().getLifeTime()))));
        }
        return lines;
    }
}
