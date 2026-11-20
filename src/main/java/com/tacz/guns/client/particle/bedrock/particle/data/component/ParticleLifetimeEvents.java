// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.EventExecutor;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

/**
 * 粒子生命周期事件组件。对应 "minecraft:particle_lifetime_events"。
 */
public record ParticleLifetimeEvents(
        List<String> creationEvent,
        List<String> expirationEvent,
        TreeMap<Float, List<String>> timeline
) implements IParticleComponentDefinition {

    @Override
    public int order() {
        return 310;
    }

    @Override
    public boolean requireUpdate() {
        return !timeline.isEmpty();
    }

    @Override
    public IParticleComponent createRuntime() {
        return new Runtime(creationEvent, expirationEvent, timeline);
    }

    /**
     * 运行时组件。持有 timeline 追踪状态。
     */
    static final class Runtime implements IParticleComponent {
        private final List<String> creationEvent;
        private final List<String> expirationEvent;
        private final TreeMap<Float, List<String>> timeline;
        private int lastTimelineIndex;

        Runtime(List<String> creationEvent, List<String> expirationEvent,
                TreeMap<Float, List<String>> timeline) {
            this.creationEvent = creationEvent;
            this.expirationEvent = expirationEvent;
            this.timeline = timeline;
        }

        @Override
        public void apply(ParticleInstance particle) {
            this.lastTimelineIndex = 0;
            // 触发创建事件
            fireEvents(creationEvent, particle);
        }

        @Override
        public void update(ParticleInstance particle) {
            if (timeline.isEmpty()) return;
            int idx = 0;
            for (Map.Entry<Float, List<String>> entry : timeline.entrySet()) {
                if (idx < lastTimelineIndex) { idx++; continue; }
                if (particle.age >= entry.getKey()) {
                    lastTimelineIndex = idx + 1;
                    fireEvents(entry.getValue(), particle);
                }
                idx++;
            }
        }

        /** 触发过期事件 */
        void fireExpiration(ParticleInstance particle) {
            fireEvents(expirationEvent, particle);
        }

        private void fireEvents(List<String> eventNames, ParticleInstance particle) {
            if (eventNames.isEmpty() || particle.emitter == null) return;
            EventExecutor.EventContext ctx = particle.emitter.getEventContext();
            if (ctx == null) return;
            ParticleEffectDefinition def = particle.emitter.getDefinition();
            EventExecutor.fireEvents(eventNames, def, ctx);
        }
    }

    public static ParticleLifetimeEvents fromJson(JsonObject obj) {
        List<String> creation = parseEventList(obj, "creation_event");
        List<String> expiration = parseEventList(obj, "expiration_event");
        TreeMap<Float, List<String>> timeline = parseTimeline(obj);
        return new ParticleLifetimeEvents(creation, expiration, timeline);
    }

    private static List<String> parseEventList(JsonObject obj, String key) {
        if (!obj.has(key)) return List.of();
        JsonElement elem = obj.get(key);
        if (elem.isJsonPrimitive()) return List.of(elem.getAsString());
        if (elem.isJsonArray()) {
            List<String> list = new ArrayList<>();
            for (JsonElement e : elem.getAsJsonArray()) list.add(e.getAsString());
            return list;
        }
        return List.of();
    }

    private static TreeMap<Float, List<String>> parseTimeline(JsonObject obj) {
        if (!obj.has("timeline")) return new TreeMap<>();
        JsonObject timelineObj = obj.getAsJsonObject("timeline");
        if (timelineObj == null) return new TreeMap<>();
        TreeMap<Float, List<String>> map = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : timelineObj.entrySet()) {
            float time = Float.parseFloat(entry.getKey());
            JsonElement val = entry.getValue();
            if (val.isJsonPrimitive()) {
                map.put(time, List.of(val.getAsString()));
            } else if (val.isJsonArray()) {
                List<String> list = new ArrayList<>();
                for (JsonElement e : val.getAsJsonArray()) list.add(e.getAsString());
                map.put(time, list);
            }
        }
        return map;
    }
}
