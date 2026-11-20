// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data;

import com.tacz.guns.client.particle.bedrock.particle.data.component.*;
import com.tacz.guns.client.particle.bedrock.particle.data.curve.ParticleCurve;
import com.tacz.guns.client.particle.bedrock.particle.data.event.*;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.*;
import net.minecraft.resources.Identifier;

import java.util.*;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.*;

/**
 * 基岩版粒子效果 JSON 解析器。
 * <p>
 * 解析时接收 {@link ParticleMolangEnvironment}，在反序列化阶段直接编译 Molang 表达式到组件中。
 */
public class ParticleEffectDeserializer {

    /**
     * 解析粒子效果定义 JSON。
     *
     * @param json  完整的粒子效果 JSON
     * @param molang Molang 编译环境
     * @return 解析后的粒子效果定义
     */
    public ParticleEffectDefinition parse(JsonElement json, ParticleMolangEnvironment molang) {
        JsonObject root = json.getAsJsonObject();
        JsonObject effect = root.getAsJsonObject("particle_effect");
        if (effect == null) throw new JsonParseException("Missing 'particle_effect'");

        // description
        JsonObject descObj = effect.getAsJsonObject("description");
        ParticleDescription description = parseDescription(descObj);

        // components
        JsonObject compObj = effect.getAsJsonObject("components");
        List<IComponent> components = compObj != null ? parseComponents(compObj, molang) : List.of();

        // curves
        JsonObject curvesObj = effect.getAsJsonObject("curves");
        Map<String, ParticleCurve> curves = curvesObj != null ? parseCurves(curvesObj, molang) : null;

        // events
        JsonObject eventsObj = effect.getAsJsonObject("events");
        Map<String, List<IEventNode>> events = eventsObj != null ? parseEvents(eventsObj, molang) : null;

        return new ParticleEffectDefinition(description.getIdentifier(), description, components, curves, events);
    }

    private ParticleDescription parseDescription(JsonObject obj) {
        String id = obj.get("identifier").getAsString();
        Identifier identifier = Identifier.parse(id);

        JsonObject renderParams = obj.getAsJsonObject("basic_render_parameters");
        ParticleDescription.Material material = ParticleDescription.Material.PARTICLES_BLEND;
        Identifier texture = Identifier.fromNamespaceAndPath("minecraft", "textures/particle/generic_0.png");
        int texW = 0, texH = 0;

        if (renderParams != null) {
            String mat = getString(renderParams, "material", "particles_blend");
            material = switch (mat) {
                case "particles_opaque" -> ParticleDescription.Material.PARTICLES_OPAQUE;
                case "particles_alpha" -> ParticleDescription.Material.PARTICLES_ALPHA;
                case "particles_add" -> ParticleDescription.Material.PARTICLES_ADD;
                case "energy_swirl" -> ParticleDescription.Material.ENERGY_SWIRL;
                case "particles_emissive" -> ParticleDescription.Material.PARTICLES_EMISSIVE;
                case "particles_blend" -> ParticleDescription.Material.PARTICLES_BLEND;
                default -> throw new JsonParseException("Unknown particle material: " + mat);
            };
            String texRaw = getString(renderParams, "texture", "textures/particle/generic_0");
            texture = resolveTexturePath(texRaw);
        }

        return new ParticleDescription(identifier, material, texture, texW, texH);
    }

    private List<IComponent> parseComponents(JsonObject obj, ParticleMolangEnvironment molang) {
        List<IComponent> components = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            IComponent component = ParticleComponentRegistry.deserialize(entry.getKey(), entry.getValue(), molang);
            if (component != null) {
                components.add(component);
            } else throw new JsonParseException("Unknown particle component: " + entry.getKey());
        }
        return components;
    }

    // ---- Curves ----

    private Map<String, ParticleCurve> parseCurves(JsonObject obj, ParticleMolangEnvironment molang) {
        Map<String, ParticleCurve> curves = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getValue().isJsonObject()) {
                curves.put(entry.getKey(), ParticleCurve.fromJson(entry.getValue().getAsJsonObject(), molang));
            }
        }
        return curves.isEmpty() ? null : curves;
    }

    // ---- Events ----

    private Map<String, List<IEventNode>> parseEvents(JsonObject obj, ParticleMolangEnvironment molang) {
        Map<String, List<IEventNode>> events = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            List<IEventNode> nodes = new ArrayList<>();
            if (entry.getValue().isJsonArray()) {
                // 数组形式: "event": [ { "particle_effect": {...} }, ... ]
                for (JsonElement elem : entry.getValue().getAsJsonArray()) {
                    if (elem.isJsonObject()) {
                        nodes.addAll(parseEventNodeObject(elem.getAsJsonObject(), molang));
                    }
                }
            } else if (entry.getValue().isJsonObject()) {
                // 对象形式: "event": { "particle_effect": {...} }
                nodes.addAll(parseEventNodeObject(entry.getValue().getAsJsonObject(), molang));
            }
            if (!nodes.isEmpty()) {
                events.put(entry.getKey(), nodes);
            }
        }
        return events.isEmpty() ? null : events;
    }

    /**
     * 解析单个事件定义对象。
     */
    private List<IEventNode> parseEventNodeObject(JsonObject obj, ParticleMolangEnvironment molang) {
        List<IEventNode> nodes = new ArrayList<>();

        if (obj.has("particle_effect")) {
            nodes.add(parseParticleEffectEvent(obj.getAsJsonObject("particle_effect"), molang));
        }
        if (obj.has("sound_effect")) {
            nodes.add(parseSoundEffectEvent(obj.get("sound_effect")));
        }
        if (obj.has("sequence")) {
            nodes.add(parseSequence(obj.getAsJsonArray("sequence"), molang));
        }
        if (obj.has("randomize")) {
            nodes.add(parseRandomize(obj.getAsJsonArray("randomize"), molang));
        }
        if (obj.has("log")) {
            nodes.add(new EventLog(obj.get("log").getAsString()));
        }
        if (obj.has("expression")) {
            nodes.add(MolangExpressionEvent.of(obj.get("expression").getAsString(), molang));
        }

        return nodes;
    }

    private ParticleEffectEvent parseParticleEffectEvent(JsonObject obj, ParticleMolangEnvironment molang) {
        String effect = obj.has("effect") ? obj.get("effect").getAsString() : "";
        ParticleEffectEvent.Type type = ParticleEffectEvent.Type.EMITTER;
        if (obj.has("type")) {
            type = ParticleEffectEvent.Type.fromString(obj.get("type").getAsString());
        }
        String preExpr = obj.has("pre_effect_expression") ? obj.get("pre_effect_expression").getAsString() : null;
        return ParticleEffectEvent.of(effect, type, preExpr, molang);
    }

    private SoundEffectEvent parseSoundEffectEvent(JsonElement elem) {
        if (elem.isJsonObject()) {
            JsonObject obj = elem.getAsJsonObject();
            String eventName = obj.has("event_name") ? obj.get("event_name").getAsString() : "";
            return new SoundEffectEvent(eventName);
        }
        return new SoundEffectEvent(elem.getAsString());
    }

    private EventSequence parseSequence(JsonArray arr, ParticleMolangEnvironment molang) {
        List<IEventNode> nodes = new ArrayList<>();
        for (JsonElement elem : arr) {
            if (elem.isJsonObject()) {
                nodes.addAll(parseEventNodeObject(elem.getAsJsonObject(), molang));
            }
        }
        return new EventSequence(nodes);
    }

    private EventRandomize parseRandomize(JsonArray arr, ParticleMolangEnvironment molang) {
        List<EventRandomize.WeightedEntry> entries = new ArrayList<>();
        for (JsonElement elem : arr) {
            if (elem.isJsonObject()) {
                JsonObject entryObj = elem.getAsJsonObject();
                float weight = entryObj.has("weight") ? entryObj.get("weight").getAsFloat() : 1f;
                List<IEventNode> nodes = parseEventNodeObject(entryObj, molang);
                for (IEventNode node : nodes) {
                    entries.add(new EventRandomize.WeightedEntry(weight, node));
                }
            }
        }
        return new EventRandomize(entries);
    }
}
