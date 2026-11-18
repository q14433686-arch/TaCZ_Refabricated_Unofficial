package com.tacz.guns.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class TargetHitTrigger extends SimpleCriterionTrigger<TargetHitTrigger.TriggerInstance> {
    public static final TargetHitTrigger INSTANCE = new TargetHitTrigger();

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, double distance) {
        this.trigger(player, instance -> instance.distance().matches(distance));
    }

    public record TriggerInstance(Optional<Holder<LootItemCondition>> player,
                                  MinMaxBounds.Doubles distance) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("distance", MinMaxBounds.Doubles.ANY).forGetter(TriggerInstance::distance)).apply(instance, TriggerInstance::new));
    }
}
