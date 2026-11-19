// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.EventExecutor;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleEmitterInstance;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;

import java.util.*;

/**
 * 发射器生命周期事件组件。对应 "minecraft:emitter_lifetime_events"。
 */
public record EmitterLifetimeEvents(
        List<String> creationEvent,
        List<String> expirationEvent,
        TreeMap<Float, List<String>> timeline,
        TreeMap<Float, List<String>> travelDistanceEvents,
        List<LoopingTravelDistanceEvent> loopingTravelDistanceEvents
) implements IEmitterComponentDefinition {

    @Override
    public int order() {
        return 510;
    }

    @Override
    public boolean requireUpdate() {
        return !timeline.isEmpty() || !travelDistanceEvents.isEmpty() || !loopingTravelDistanceEvents.isEmpty();
    }

    @Override
    public IEmitterComponent createRuntime() {
        return new Runtime(creationEvent, expirationEvent, timeline, travelDistanceEvents, loopingTravelDistanceEvents);
    }

    public record LoopingTravelDistanceEvent(float distance, List<String> effects) {}

    /**
     * 运行时组件。持有事件追踪状态。
     */
    static final class Runtime implements IEmitterComponent {
        private final List<String> creationEvent;
        private final List<String> expirationEvent;
        private final TreeMap<Float, List<String>> timeline;
        private final TreeMap<Float, List<String>> travelDistanceEvents;
        private final List<LoopingTravelDistanceEvent> loopingTravelDistanceEvents;

        // 运行时状态
        private int lastTimelineIndex;
        private int lastTravelDistIndex;
        private float[] loopingTravelDistAccum;
        private float travelDistance;
        private float prevEmitterX, prevEmitterY, prevEmitterZ;
        private boolean hasPrevPosition;

        Runtime(List<String> creationEvent, List<String> expirationEvent,
                TreeMap<Float, List<String>> timeline,
                TreeMap<Float, List<String>> travelDistanceEvents,
                List<LoopingTravelDistanceEvent> loopingEvents) {
            this.creationEvent = creationEvent;
            this.expirationEvent = expirationEvent;
            this.timeline = timeline;
            this.travelDistanceEvents = travelDistanceEvents;
            this.loopingTravelDistanceEvents = loopingEvents;
            this.loopingTravelDistAccum = new float[loopingEvents.size()];
        }

        @Override
        public void apply(ParticleEmitterInstance emitter) {
            lastTimelineIndex = 0;
            lastTravelDistIndex = 0;
            travelDistance = 0;
            hasPrevPosition = false;
            Arrays.fill(loopingTravelDistAccum, 0f);
        }

        @Override
        public void update(ParticleEmitterInstance emitter) {
            EventExecutor.EventContext ctx = emitter.getEventContext();
            if (ctx == null) return;
            ParticleEffectDefinition def = emitter.getDefinition();

            float emitterAge = emitter.getEmitterAge();

            // timeline 事件
            if (!timeline.isEmpty()) {
                int idx = 0;
                for (Map.Entry<Float, List<String>> entry : timeline.entrySet()) {
                    if (idx < lastTimelineIndex) { idx++; continue; }
                    if (emitterAge >= entry.getKey()) {
                        lastTimelineIndex = idx + 1;
                        EventExecutor.fireEvents(entry.getValue(), def, ctx);
                    }
                    idx++;
                }
            }

            // 计算移动距离
            updateTravelDistance(emitter);

            // travel_distance_events
            if (!travelDistanceEvents.isEmpty()) {
                int idx = 0;
                for (Map.Entry<Float, List<String>> entry : travelDistanceEvents.entrySet()) {
                    if (idx < lastTravelDistIndex) { idx++; continue; }
                    if (travelDistance >= entry.getKey()) {
                        lastTravelDistIndex = idx + 1;
                        EventExecutor.fireEvents(entry.getValue(), def, ctx);
                    }
                    idx++;
                }
            }

            // looping_travel_distance_events
            for (int i = 0; i < loopingTravelDistanceEvents.size(); i++) {
                LoopingTravelDistanceEvent loopEvent = loopingTravelDistanceEvents.get(i);
                if (travelDistance - loopingTravelDistAccum[i] >= loopEvent.distance()) {
                    loopingTravelDistAccum[i] = travelDistance;
                    EventExecutor.fireEvents(loopEvent.effects(), def, ctx);
                }
            }
        }

        private void updateTravelDistance(ParticleEmitterInstance emitter) {
            Matrix4f worldTransform = emitter.getWorldTransform();
            float curX = worldTransform.m30();
            float curY = worldTransform.m31();
            float curZ = worldTransform.m32();
            if (hasPrevPosition) {
                float dx = curX - prevEmitterX;
                float dy = curY - prevEmitterY;
                float dz = curZ - prevEmitterZ;
                float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 0) travelDistance += dist;
            }
            prevEmitterX = curX;
            prevEmitterY = curY;
            prevEmitterZ = curZ;
            hasPrevPosition = true;
        }

        /** 触发创建事件 */
        void fireCreation(ParticleEmitterInstance emitter) {
            EventExecutor.EventContext ctx = emitter.getEventContext();
            if (ctx != null && !creationEvent.isEmpty()) {
                EventExecutor.fireEvents(creationEvent, emitter.getDefinition(), ctx);
            }
        }

        /** 触发过期事件 */
        void fireExpiration(ParticleEmitterInstance emitter) {
            EventExecutor.EventContext ctx = emitter.getEventContext();
            if (ctx != null && !expirationEvent.isEmpty()) {
                EventExecutor.fireEvents(expirationEvent, emitter.getDefinition(), ctx);
            }
        }
    }

    public static EmitterLifetimeEvents fromJson(JsonObject obj) {
        List<String> creation = parseEventList(obj, "creation_event");
        List<String> expiration = parseEventList(obj, "expiration_event");
        TreeMap<Float, List<String>> timeline = parseTimeline(obj, "timeline");
        TreeMap<Float, List<String>> travelDistance = parseTimeline(obj, "travel_distance_events");
        List<LoopingTravelDistanceEvent> looping = parseLoopingTravelDistance(obj);
        return new EmitterLifetimeEvents(creation, expiration, timeline, travelDistance, looping);
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

    private static TreeMap<Float, List<String>> parseTimeline(JsonObject obj, String key) {
        if (!obj.has(key)) return new TreeMap<>();
        JsonObject timelineObj = obj.getAsJsonObject(key);
        if (timelineObj == null) return new TreeMap<>();
        TreeMap<Float, List<String>> map = new TreeMap<>();
        for (Map.Entry<String, JsonElement> entry : timelineObj.entrySet()) {
            float time = Float.parseFloat(entry.getKey());
            map.put(time, parseEventValue(entry.getValue()));
        }
        return map;
    }

    private static List<String> parseEventValue(JsonElement elem) {
        if (elem.isJsonPrimitive()) return List.of(elem.getAsString());
        if (elem.isJsonArray()) {
            List<String> list = new ArrayList<>();
            for (JsonElement e : elem.getAsJsonArray()) list.add(e.getAsString());
            return list;
        }
        return List.of();
    }

    private static List<LoopingTravelDistanceEvent> parseLoopingTravelDistance(JsonObject obj) {
        if (!obj.has("looping_travel_distance_events")) return List.of();
        JsonArray arr = obj.getAsJsonArray("looping_travel_distance_events");
        if (arr == null) return List.of();
        List<LoopingTravelDistanceEvent> list = new ArrayList<>();
        for (JsonElement elem : arr) {
            JsonObject entry = elem.getAsJsonObject();
            float distance = entry.has("distance") ? entry.get("distance").getAsFloat() : 1f;
            List<String> effects = new ArrayList<>();
            if (entry.has("effects")) {
                for (JsonElement e : entry.getAsJsonArray("effects")) effects.add(e.getAsString());
            }
            list.add(new LoopingTravelDistanceEvent(distance, effects));
        }
        return list;
    }
}
