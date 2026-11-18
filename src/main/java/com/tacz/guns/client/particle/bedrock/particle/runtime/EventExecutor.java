// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.runtime;

import com.tacz.guns.GunMod;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDefinition;
import com.tacz.guns.client.particle.bedrock.particle.data.event.*;
import com.tacz.guns.client.particle.bedrock.particle.resource.ParticleDefinitionLoader;
import com.tacz.guns.client.particle.bedrock.particle.world.WorldEmitterManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 粒子事件执行引擎。
 */
public final class EventExecutor {

    private EventExecutor() {}

    /**
     * 事件执行上下文。
     */
    public record EventContext(
            ParticleEmitterInstance emitter,
            ParticleMolangEnvironment molang,
            @Nullable ClientLevel level,
            @Nullable Vec3 position,
            Random random  // 本地随机源，用于 EventRandomize
    ) {
        public EventContext(ParticleEmitterInstance emitter, ParticleMolangEnvironment molang,
                            @Nullable ClientLevel level, @Nullable Vec3 position) {
            this(emitter, molang, level, position, new Random());
        }
    }

    public static void fireEvents(List<String> eventNames, ParticleEffectDefinition definition, EventContext ctx) {
        if (eventNames.isEmpty()) return;
        Map<String, List<IEventNode>> allEvents = definition.getEvents();
        if (allEvents.isEmpty()) return;

        for (String name : eventNames) {
            List<IEventNode> nodes = allEvents.get(name);
            if (nodes == null) continue;
            for (IEventNode node : nodes) {
                execute(node, ctx);
            }
        }
    }

    public static void execute(IEventNode node, EventContext ctx) {
        if (node instanceof EventSequence seq) {
            for (IEventNode child : seq.nodes()) {
                execute(child, ctx);
            }
        } else if (node instanceof EventRandomize randomize) {
            executeRandomize(randomize, ctx);
        } else if (node instanceof ParticleEffectEvent effect) {
            executeParticleEffect(effect, ctx);
        } else if (node instanceof SoundEffectEvent sound) {
            executeSoundEffect(sound, ctx);
        } else if (node instanceof MolangExpressionEvent expr) {
            executeMolangExpression(expr, ctx);
        } else if (node instanceof EventLog log) {
            executeLog(log, ctx);
        }
    }

    private static void executeRandomize(EventRandomize randomize, EventContext ctx) {
        List<EventRandomize.WeightedEntry> entries = randomize.entries();
        if (entries.isEmpty()) return;

        float totalWeight = 0;
        for (EventRandomize.WeightedEntry entry : entries) {
            totalWeight += entry.weight();
        }
        if (totalWeight <= 0) return;

        float roll = ctx.random().nextFloat() * totalWeight;
        float cumulative = 0;
        for (EventRandomize.WeightedEntry entry : entries) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                execute(entry.node(), ctx);
                return;
            }
        }
        execute(entries.get(entries.size() - 1).node(), ctx);
    }

    private static void executeParticleEffect(ParticleEffectEvent effect, EventContext ctx) {
        if (ctx.level == null || ctx.position == null) return;

        Identifier effectId = Identifier.tryParse(effect.effect());
        if (effectId == null) return;
        ParticleEffectDefinition childDef = ParticleDefinitionLoader.getInstance().getDefinition(effectId);
        if (childDef == null) {
            GunMod.LOGGER.debug("Particle effect event references unknown effect: {}", effect.effect());
            return;
        }

        WorldEmitterManager.getInstance().addEmitter(ctx.level,
                new org.joml.Matrix4f().translation((float)ctx.position.x,(float)ctx.position.y,(float)ctx.position.z),
                Vec3.ZERO,childDef,effect.preEffectExpression());
    }

    private static void executeSoundEffect(SoundEffectEvent sound, EventContext ctx) {
        if (ctx.level == null || ctx.position == null) return;
        String eventName = sound.eventName();
        if (eventName == null || eventName.isEmpty()) return;

        try {
            Identifier soundId = Identifier.parse(eventName);
            SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(soundId);
            ctx.level.playLocalSound(
                    ctx.position.x, ctx.position.y, ctx.position.z,
                    soundEvent, SoundSource.AMBIENT, 1.0f, 1.0f, false
            );
        } catch (Exception e) {
            GunMod.LOGGER.warn("Failed to play sound effect: {}", eventName, e);
        }
    }

    private static void executeMolangExpression(MolangExpressionEvent expr, EventContext ctx) {
        // 使用预编译的表达式
        if (expr.compiledExpression() == null) return;
        try {
            expr.compiledExpression().evaluate(ctx.molang.getContext());
        } catch (Exception e) {
            GunMod.LOGGER.warn("Failed to evaluate event molang expression: {}", expr.expression(), e);
        }
    }

    private static void executeLog(EventLog log, EventContext ctx) {
        GunMod.LOGGER.info("[ParticleEvent] {}", log.message());
    }
}
