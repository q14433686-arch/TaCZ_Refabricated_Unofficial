// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.motion;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponent;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponentDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getBoolean;
import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolang;

/**
 * 粒子碰撞运动组件。对应 "minecraft:particle_motion_collision"。
 */
public record ParticleMotionCollision(
        @Nullable MolangExpression enabled,
        float collisionDrag,
        float coefficientOfRestitution,
        float collisionRadius,
        boolean expireOnContact,
        List<CollisionEvent> events
) implements IParticleComponentDefinition, IParticleComponent {

    @Override public int order() { return 350; }
    @Override public boolean requireUpdate() { return enabled != null || !events.isEmpty(); }

    public record CollisionEvent(String event, float minSpeed) {}

    public static ParticleMotionCollision fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        MolangExpression enabled = obj.has("enabled") ? molang.compile(getMolang(obj, "enabled", "1")) : null;
        float drag = obj.has("collision_drag") ? obj.get("collision_drag").getAsFloat() : 0;
        float restitution = obj.has("coefficient_of_restitution") ? obj.get("coefficient_of_restitution").getAsFloat() : 0;
        float radius = obj.has("collision_radius") ? obj.get("collision_radius").getAsFloat() : 0;
        boolean expire = getBoolean(obj, "expire_on_contact", false);
        return new ParticleMotionCollision(enabled, drag, restitution, radius, expire, parseEvents(obj));
    }

    private static List<CollisionEvent> parseEvents(JsonObject obj) {
        if (!obj.has("events") || !obj.get("events").isJsonArray()) return List.of();
        JsonArray arr = obj.getAsJsonArray("events");
        List<CollisionEvent> list = new ArrayList<>();
        for (JsonElement e : arr) {
            if (e.isJsonObject()) {
                JsonObject eventObj = e.getAsJsonObject();
                String event = eventObj.has("event") ? eventObj.get("event").getAsString() : "";
                float minSpeed = eventObj.has("min_speed") ? eventObj.get("min_speed").getAsFloat() : 2f;
                if (!event.isEmpty()) list.add(new CollisionEvent(event, minSpeed));
            }
        }
        return list;
    }
}
