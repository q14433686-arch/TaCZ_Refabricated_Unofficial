package me.xjqsh.lrtactical.item.consumable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import me.xjqsh.lrtactical.util.PotionTooltipUtil;
import me.xjqsh.lrtactical.util.TooltipLine;
import me.xjqsh.lrtactical.util.TooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ConsumableData {
    @SerializedName("use_duration")
    private int useDuration = 32;
    @SerializedName("cooldown")
    private int cooldown = 0;
    @SerializedName("cooldown_category")
    private Identifier cooldownCategory = null;
    @SerializedName("stack_size")
    private int stackSize = 1;
    @SerializedName("max_durability")
    private int maxDurability = 0;
    @SerializedName("durability_damage")
    private int durabilityDamage = 1;
    @SerializedName("draw_time")
    private int drawTime = 0;
    @SerializedName("put_away_time")
    private int putAwayTime = 0;
    @SerializedName("heal")
    private float heal = 0f;
    @SerializedName("food")
    private int food = 0;
    @SerializedName("saturation")
    private float saturation = 0f;
    @SerializedName("effects")
    private List<EffectData> effects = Collections.emptyList();
    @SerializedName("remove_effects")
    private List<RemoveEffectSelector> removeEffects = Collections.emptyList();
    @SerializedName("use_mode")
    private UseMode useMode = UseMode.HOLD;

    public int getUseDuration() { return useDuration; }
    public UseMode getUseMode() { return useMode; }
    public boolean isToggleUse() { return useMode == UseMode.TOGGLE; }
    public int getCooldown() { return cooldown; }
    public Identifier getCooldownCategory() { return cooldownCategory; }
    public int getStackSize() { return stackSize; }
    public int getMaxDurability() { return maxDurability; }
    public int getDurabilityDamage() { return durabilityDamage; }
    public boolean hasDurability() { return maxDurability > 0; }
    public int getDrawTime() { return drawTime; }
    public int getPutAwayTime() { return putAwayTime; }
    public float getHeal() { return heal; }
    public int getFood() { return food; }
    public float getSaturation() { return saturation; }
    public List<EffectData> getEffects() { return effects; }
    public List<RemoveEffectSelector> getRemoveEffects() { return removeEffects; }

    public List<TooltipLine> getTooltipLines() {
        List<TooltipLine> lines = new ArrayList<>();
        if (getHeal() > 0f) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.consumable.heal", TooltipUtil.format(getHeal()))));
        }
        if (getFood() > 0 || getSaturation() > 0f) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.consumable.food", getFood(), TooltipUtil.format(getSaturation()))));
        }
        List<PotionTooltipUtil.EffectWithChance> effects = new ArrayList<>();
        for (EffectData effectData : getEffects()) {
            MobEffectInstance effect = effectData.createInstance();
            if (effect != null) {
                effects.add(new PotionTooltipUtil.EffectWithChance(effect, effectData.getChance()));
            }
        }
        List<Component> effectLines = new ArrayList<>();
        PotionTooltipUtil.addPotionTooltip(effects, effectLines);
        effectLines.forEach(line -> lines.add(TooltipLine.collapsible(line)));

        for (RemoveEffectSelector selector : getRemoveEffects()) {
            if (selector.isCategory() && selector.getCategory() != null) {
                String categoryKey = switch (selector.getCategory()) {
                    case BENEFICIAL -> "tooltip.lrtactical.consumable.effect_category.beneficial";
                    case HARMFUL -> "tooltip.lrtactical.consumable.effect_category.harmful";
                    case NEUTRAL -> "tooltip.lrtactical.consumable.effect_category.neutral";
                };
                lines.add(TooltipLine.collapsible(Component.translatable(
                        "tooltip.lrtactical.consumable.remove_effects_by_category",
                        Component.translatable(categoryKey)).withStyle(ChatFormatting.GRAY)));
            } else if (selector.getEffect() != null) {
                Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(selector.getEffect()).orElse(null);
                if (effect != null) {
                    lines.add(TooltipLine.collapsible(Component.translatable(
                            "tooltip.lrtactical.consumable.remove_effect",
                            Component.translatable(effect.value().getDescriptionId()))
                            .withStyle(ChatFormatting.GRAY)));
                }
            }
        }
        if (getMaxDurability() > 1 && getDurabilityDamage() > 0) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.consumable.uses",
                    getMaxDurability() / getDurabilityDamage())));
        }
        if (getUseDuration() > 0) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.consumable.use_duration",
                    TooltipUtil.formatTicks(getUseDuration()))));
        }
        if (getCooldown() > 0) {
            lines.add(TooltipLine.normal(Component.translatable(
                    "tooltip.lrtactical.consumable.cooldown",
                    TooltipUtil.formatTicks(getCooldown()))));
        }
        return lines;
    }

    public enum UseMode {
        @SerializedName("hold") HOLD,
        @SerializedName("toggle") TOGGLE
    }

    public static class RemoveEffectSelector {
        @Nullable private final Identifier effect;
        @Nullable private final MobEffectCategory category;

        private RemoveEffectSelector(@Nullable Identifier effect, @Nullable MobEffectCategory category) {
            this.effect = effect;
            this.category = category;
        }
        public static RemoveEffectSelector effect(Identifier effect) { return new RemoveEffectSelector(effect, null); }
        public static RemoveEffectSelector category(MobEffectCategory category) { return new RemoveEffectSelector(null, category); }
        public boolean isCategory() { return category != null; }
        @Nullable public Identifier getEffect() { return effect; }
        @Nullable public MobEffectCategory getCategory() { return category; }

        public enum CategoryAlias {
            BENEFICIAL("@beneficial", MobEffectCategory.BENEFICIAL),
            HARMFUL("@harmful", MobEffectCategory.HARMFUL),
            NEUTRAL("@neutral", MobEffectCategory.NEUTRAL);
            private static final Map<String, CategoryAlias> BY_ID = Arrays.stream(values())
                    .collect(Collectors.toMap(CategoryAlias::getId, Function.identity()));
            private final String id;
            private final MobEffectCategory category;
            CategoryAlias(String id, MobEffectCategory category) { this.id = id; this.category = category; }
            public String getId() { return id; }
            public MobEffectCategory getCategory() { return category; }
            public static CategoryAlias byId(String id) { return BY_ID.get(id); }
        }

        public static class Deserializer implements JsonDeserializer<RemoveEffectSelector> {
            @Override
            public RemoveEffectSelector deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                    throws JsonParseException {
                if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
                    throw new JsonParseException("Expected remove effect selector to be a string");
                }
                String value = json.getAsString();
                CategoryAlias alias = CategoryAlias.byId(value);
                if (alias != null) {
                    return RemoveEffectSelector.category(alias.getCategory());
                }
                Identifier effectId = Identifier.tryParse(value);
                if (effectId == null) {
                    throw new JsonParseException("Invalid effect id or category selector \"" + value + "\"");
                }
                return RemoveEffectSelector.effect(effectId);
            }
        }
    }

    public static class EffectData {
        @SerializedName("id") private Identifier id;
        @SerializedName("duration") private int duration = 0;
        @SerializedName("amplifier") private int amplifier = 0;
        @SerializedName("chance") private float chance = 1f;
        @SerializedName("ambient") private boolean ambient = false;
        @SerializedName("visible") private boolean visible = true;
        @SerializedName("show_icon") private boolean showIcon = true;

        public Identifier getId() { return id; }
        public float getChance() { return chance; }
        @Nullable
        public MobEffectInstance createInstance() {
            Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(id).orElse(null);
            if (effect == null) {
                return null;
            }
            return new MobEffectInstance(effect, duration, amplifier, ambient, visible, showIcon);
        }
    }
}
