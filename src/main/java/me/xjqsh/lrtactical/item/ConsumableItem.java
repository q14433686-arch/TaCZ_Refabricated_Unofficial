package me.xjqsh.lrtactical.item;

import me.xjqsh.lrtactical.api.item.IConsumable;
import me.xjqsh.lrtactical.capability.CustomItemCoolDowns;
import me.xjqsh.lrtactical.init.ModCapabilities;
import me.xjqsh.lrtactical.item.consumable.ConsumableData;
import me.xjqsh.lrtactical.item.index.ConsumableIndex;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 基础消耗品实现（药品/食物）。
 *
 * <p>服务端效果与组件自愈之外，装了内容包时还会走 Bedrock 模型 + Lua 动画
 * （{@link me.xjqsh.lrtactical.client.renderer.item.ConsumableItemRenderer}）；
 * 没装内容包则由 {@code items/consumable.json} 的条件分流回退到原版占位模型。
 */
public class ConsumableItem extends Item implements IConsumable, com.tacz.guns.api.item.IAnimationItem,
        cn.sh1rocu.tacz.api.extension.IItem {
    public ConsumableItem(Properties properties) {
        super(properties.stacksTo(Item.ABSOLUTE_MAX_STACK_SIZE));
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull ServerLevel level,
                              @NotNull Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        IConsumable.applyComponents(stack, false);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return this.getConsumableIndex(stack).map(index -> index.getData().getUseDuration()).orElse(0);
    }

    @Override
    public @NotNull ItemUseAnimation getUseAnimation(@NotNull ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public @NotNull InteractionResult use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND || player.isUsingItem()) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        // 两端都查冷却，不再只查服务端。原来只有服务端查、客户端一律乐观放行，
        // 于是「服务端在冷却中、客户端却 startUsingItem」的分叉每次都会发生：
        // 客户端走完这轮读条也不会消耗任何东西（finishUsingItem 的效果段有
        // !level.isClientSide() 门禁），表现为「读了个空条」。
        // 客户端这张表由 ServerMessageCustomCooldown 同步、由 PlayerTickEvent.START
        // 每客户端游戏刻推进（PlayerMixin 注入 Player#tick HEAD，不分端），
        // 偏差方向是「只会多拒一会儿」，代价远小于分叉。完整论证见
        // ThrowableItem#use 的方法注释（同一套冷却机制）。
        CustomItemCoolDowns coolDowns = ModCapabilities.coolDowns(player);
        boolean onCooldown = getCoolDownId(stack).map(coolDowns::isOnCooldown).orElse(false);
        if (onCooldown) {
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
                                               @NotNull LivingEntity entity) {
        getConsumableIndex(stack).ifPresent(index -> {
            if (!level.isClientSide()) {
                applyEffects(entity, index);
                if (entity instanceof Player player) {
                    IdentifierCooldown.add(player, index);
                    if (!player.getAbilities().instabuild) {
                        consumeAfterUse(stack, index);
                    }
                } else {
                    consumeAfterUse(stack, index);
                }
            }
        });
        return stack;
    }

    private static final class IdentifierCooldown {
        static void add(Player player, ConsumableIndex index) {
            var cooldownId = index.getData().getCooldownCategory();
            if (cooldownId != null && index.getData().getCooldown() > 0) {
                ModCapabilities.coolDowns(player).addCooldown(cooldownId, index.getData().getCooldown());
            }
        }
    }

    private void consumeAfterUse(ItemStack stack, ConsumableIndex index) {
        ConsumableData data = index.getData();
        if (data.hasDurability()) {
            int newDamage = stack.getDamageValue() + data.getDurabilityDamage();
            if (newDamage >= data.getMaxDurability()) {
                stack.shrink(1);
            } else {
                stack.set(DataComponents.DAMAGE, newDamage);
            }
        } else {
            stack.shrink(1);
        }
    }

    private void applyEffects(LivingEntity entity, ConsumableIndex index) {
        ConsumableData data = index.getData();
        if (data.getHeal() > 0f) {
            entity.heal(data.getHeal());
        }
        for (ConsumableData.RemoveEffectSelector selector : data.getRemoveEffects()) {
            removeEffect(entity, selector);
        }
        for (ConsumableData.EffectData effectData : data.getEffects()) {
            if (entity.getRandom().nextFloat() > effectData.getChance()) {
                continue;
            }
            MobEffectInstance effect = effectData.createInstance();
            if (effect != null) {
                entity.addEffect(effect);
            }
        }
        if (entity instanceof Player player && (data.getFood() > 0 || data.getSaturation() > 0)) {
            player.getFoodData().eat(data.getFood(), data.getSaturation());
        }
    }

    private void removeEffect(LivingEntity entity, ConsumableData.RemoveEffectSelector selector) {
        if (selector.isCategory()) {
            removeEffectsByCategory(entity, selector.getCategory());
            return;
        }
        if (selector.getEffect() == null) {
            return;
        }
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT.get(selector.getEffect()).orElse(null);
        if (effect != null) {
            entity.removeEffect(effect);
        }
    }

    private void removeEffectsByCategory(LivingEntity entity, @Nullable MobEffectCategory category) {
        if (category == null) {
            return;
        }
        List<Holder<MobEffect>> effects = entity.getActiveEffects().stream()
                .map(MobEffectInstance::getEffect)
                .filter(effect -> effect.value().getCategory() == category)
                .toList();
        for (Holder<MobEffect> effect : effects) {
            entity.removeEffect(effect);
        }
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return this.getConsumableIndex(stack)
                .<Component>map(index -> Component.translatable(index.getDescriptionId()))
                .orElseGet(() -> super.getName(stack));
    }

    @Override
    public boolean isSame(ItemStack stack1, ItemStack stack2) {
        return IConsumable.super.isSame(stack1, stack2);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return this.getConsumableIndex(stack).isPresent()
                ? Optional.of(new me.xjqsh.lrtactical.inventory.tooltip.ConsumableTooltip(stack))
                : Optional.empty();
    }

    /**
     * 自定义渲染器：接入 TACZ 的 Bedrock 模型 + Lua 动画状态机管线。
     *
     * <p>说明与注意事项见 {@link me.xjqsh.lrtactical.item.MeleeItem#getCustomRenderer()}。
     */
    @Override
    @net.fabricmc.api.Environment(net.fabricmc.api.EnvType.CLIENT)
    public cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry.DynamicItemRenderer getCustomRenderer() {
        return me.xjqsh.lrtactical.client.renderer.item.ConsumableItemRenderer.INSTANCE.get();
    }

    /**
     * 阻止玩家手臂挥动 —— 使用动作由 Lua 动画状态机负责，vanilla 摆手会与之打架。
     *
     * <p>与 {@code MeleeItem} / {@code ThrowableItem} 同样处理。
     */
    @Override
    public boolean tacz$onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }
}
