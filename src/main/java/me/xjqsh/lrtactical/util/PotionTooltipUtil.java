package me.xjqsh.lrtactical.util;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 26.2 holder-based port of the vanilla-style potion tooltip formatter. */
public final class PotionTooltipUtil {
    private PotionTooltipUtil() {
    }

    public record EffectWithChance(MobEffectInstance effect, float chance) {
    }

    private record AttributeLine(Holder<Attribute> attribute, AttributeModifier modifier) {
    }

    public static void addPotionTooltip(List<EffectWithChance> effects, List<Component> output) {
        List<AttributeLine> attributes = new ArrayList<>();
        for (EffectWithChance entry : effects) {
            MobEffectInstance instance = entry.effect();
            MutableComponent name = Component.translatable(instance.getDescriptionId());
            if (instance.getAmplifier() > 0) {
                name = Component.translatable("potion.withAmplifier", name,
                        Component.translatable("potion.potency." + instance.getAmplifier()));
            }
            boolean duration = !instance.endsWithin(20);
            boolean chance = entry.chance() < 1.0F;
            Component line;
            if (duration && chance) {
                line = Component.translatable("tooltip.lrtactical.consumable.effect.with_duration_and_chance",
                        name, TooltipUtil.formatTicks(instance.getDuration()), formatChance(entry.chance()));
            } else if (duration) {
                line = Component.translatable("potion.withDuration", name,
                        TooltipUtil.formatTicks(instance.getDuration()));
            } else if (chance) {
                line = Component.translatable("tooltip.lrtactical.consumable.effect.with_chance",
                        name, formatChance(entry.chance()));
            } else {
                line = name;
            }
            output.add(line.copy().withStyle(instance.getEffect().value().getCategory().getTooltipFormatting()));
            instance.getEffect().value().createModifiers(instance.getAmplifier(),
                    (attribute, modifier) -> attributes.add(new AttributeLine(attribute, modifier)));
        }

        if (attributes.isEmpty()) {
            return;
        }
        output.add(CommonComponents.EMPTY);
        output.add(Component.translatable("potion.whenDrank").withStyle(ChatFormatting.DARK_PURPLE));
        for (AttributeLine entry : attributes) {
            AttributeModifier modifier = entry.modifier();
            double amount = modifier.amount();
            double shown = modifier.operation() == AttributeModifier.Operation.ADD_VALUE
                    ? amount : amount * 100.0D;
            if (amount > 0.0D) {
                output.add(Component.translatable(
                        "attribute.modifier.plus." + modifier.operation().id(),
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(shown),
                        Component.translatable(entry.attribute().value().getDescriptionId()))
                        .withStyle(ChatFormatting.BLUE));
            } else if (amount < 0.0D) {
                output.add(Component.translatable(
                        "attribute.modifier.take." + modifier.operation().id(),
                        ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(-shown),
                        Component.translatable(entry.attribute().value().getDescriptionId()))
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    private static Component formatChance(float chance) {
        return Component.literal(String.format(Locale.ROOT, "%.0f%%", chance * 100.0F));
    }
}
