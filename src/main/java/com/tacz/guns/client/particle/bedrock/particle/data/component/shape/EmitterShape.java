// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.shape;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IEmitterComponent;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IEmitterComponentDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;
import java.util.Random;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.*;

/**
 * 发射器形状组件接口，决定粒子的初始位置和方向。
 */
public interface EmitterShape extends IEmitterComponentDefinition, IEmitterComponent {

    @Override
    default int order() { return 0; }

    enum PlaneNormal { X, Y, Z, CUSTOM }
    enum DirectionMode { OUTWARDS, INWARDS, CUSTOM }

    MolangExpression[] offset();

    @Nullable MolangExpression[] direction();

    DirectionMode directionMode();

    void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random);

    default void applyDirection(ParticleInstance p, MolangContext<?> ctx, Random random, float speed) {
        if (speed == 0) return;
        float dx, dy, dz;
        DirectionMode dirMode = directionMode();
        if (dirMode == DirectionMode.CUSTOM && direction() != null) {
            dx = (float) direction()[0].evaluate(ctx);
            dy = (float) direction()[1].evaluate(ctx);
            dz = (float) direction()[2].evaluate(ctx);
        } else {
            float ox = (float) offset()[0].evaluate(ctx);
            float oy = (float) offset()[1].evaluate(ctx);
            float oz = (float) offset()[2].evaluate(ctx);
            dx = p.x - ox;
            dy = p.y - oy;
            dz = p.z - oz;
        }
        if (dirMode == DirectionMode.INWARDS) { dx = -dx; dy = -dy; dz = -dz; }
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 0.0001f) {
            dx /= len; dy /= len; dz /= len;
        } else {
            float theta = (float) (random.nextFloat() * Math.PI * 2);
            float phi = (float) (Math.acos(2 * random.nextFloat() - 1));
            dx = (float) (Math.sin(phi) * Math.cos(theta));
            dy = (float) Math.cos(phi);
            dz = (float) (Math.sin(phi) * Math.sin(theta));
        }
        p.vx = dx * speed; p.vy = dy * speed; p.vz = dz * speed;
    }

    // ===== JSON parsing helpers =====

    record DirectionParseResult(@Nullable MolangExpression[] direction, DirectionMode mode) {}

    static MolangExpression[] compileArray3(ParticleMolangEnvironment molang, String[] exprs) {
        return new MolangExpression[]{
                molang.compile(exprs[0]), molang.compile(exprs[1]), molang.compile(exprs[2])};
    }

    static DirectionParseResult parseShapeDirection(JsonObject obj, ParticleMolangEnvironment molang) {
        if (!obj.has("direction")) return new DirectionParseResult(null, DirectionMode.OUTWARDS);
        JsonElement elem = obj.get("direction");
        if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
            return new DirectionParseResult(null,
                    "inwards".equals(elem.getAsString().toLowerCase()) ? DirectionMode.INWARDS : DirectionMode.OUTWARDS);
        }
        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            return new DirectionParseResult(compileArray3(molang, new String[]{
                    molangFromElement(arr.size() > 0 ? arr.get(0) : null, "0"),
                    molangFromElement(arr.size() > 1 ? arr.get(1) : null, "0"),
                    molangFromElement(arr.size() > 2 ? arr.get(2) : null, "0")}), DirectionMode.CUSTOM);
        }
        return new DirectionParseResult(null, DirectionMode.OUTWARDS);
    }

    static PlaneNormal parsePlaneNormal(JsonObject obj) {
        if (!obj.has("plane_normal")) return PlaneNormal.Y;
        JsonElement elem = obj.get("plane_normal");
        if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
            return switch (elem.getAsString().toLowerCase()) {
                case "x" -> PlaneNormal.X;
                case "z" -> PlaneNormal.Z;
                default -> PlaneNormal.Y;
            };
        }
        return PlaneNormal.CUSTOM;
    }

    static EmitterShape fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        MolangExpression[] offset = compileArray3(molang, getMolangArray3(obj, "offset", "0", "0", "0"));
        DirectionParseResult dirResult = parseShapeDirection(obj, molang);

        return switch (key) {
            case "minecraft:emitter_shape_point" ->
                    new EmitterShapePoint(offset, dirResult.direction, dirResult.mode);
            case "minecraft:emitter_shape_sphere" ->
                    new EmitterShapeSphere(offset, molang.compile(getMolang(obj, "radius", "1")),
                            getBoolean(obj, "surface_only", false), dirResult.direction, dirResult.mode);
            case "minecraft:emitter_shape_box" ->
                    new EmitterShapeBox(offset, compileArray3(molang, getMolangArray3(obj, "half_dimensions", "0.5", "0.5", "0.5")),
                            getBoolean(obj, "surface_only", false), dirResult.direction, dirResult.mode);
            case "minecraft:emitter_shape_disc" ->
                    new EmitterShapeDisc(offset, molang.compile(getMolang(obj, "radius", "1")),
                            parsePlaneNormal(obj), getBoolean(obj, "surface_only", false),
                            dirResult.direction, dirResult.mode);
            case "minecraft:emitter_shape_entity_aabb" ->
                    new EmitterShapeEntityAABB(offset, getBoolean(obj, "surface_only", false),
                            dirResult.direction, dirResult.mode);
            default -> throw new IllegalArgumentException("Unknown emitter shape key: " + key);
        };
    }
}
