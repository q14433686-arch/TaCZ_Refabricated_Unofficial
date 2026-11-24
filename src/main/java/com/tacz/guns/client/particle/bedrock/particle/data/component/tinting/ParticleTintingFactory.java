// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.tinting;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponentDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.*;

/**
 * Tinting 组件工厂。根据 JSON 结构分派到 StaticColor 或 GradientColor。
 */
public final class ParticleTintingFactory {

    private ParticleTintingFactory() {}

    public static IParticleComponentDefinition fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        if (obj.has("color")) {
            JsonElement colorElem = obj.get("color");

            if (colorElem.isJsonArray()) {
                JsonArray arr = colorElem.getAsJsonArray();
                return new ParticleTintingStatic(
                        molang.compile(molangFromElement(arr.get(0), "1")),
                        molang.compile(molangFromElement(arr.get(1), "1")),
                        molang.compile(molangFromElement(arr.get(2), "1")),
                        arr.size() > 3 ? molang.compile(molangFromElement(arr.get(3), "1")) : null);
            }

            if (colorElem.isJsonObject()) {
                JsonObject colorObj = colorElem.getAsJsonObject();
                if (colorObj.has("interpolant")) {
                    return parseGradientColor(colorObj, molang);
                }
                return new ParticleTintingStatic(
                        molang.compile(getMolang(colorObj, "r", "1")),
                        molang.compile(getMolang(colorObj, "g", "1")),
                        molang.compile(getMolang(colorObj, "b", "1")),
                        colorObj.has("a") ? molang.compile(getMolang(colorObj, "a", "1")) : null);
            }

            if (colorElem.isJsonPrimitive() && colorElem.getAsJsonPrimitive().isString()) {
                float[] rgba = parseHexColor(colorElem.getAsString());
                return new ParticleTintingStatic(
                        MolangExpression.constant(rgba[0]), MolangExpression.constant(rgba[1]),
                        MolangExpression.constant(rgba[2]), MolangExpression.constant(rgba[3]));
            }
        }
        return new ParticleTintingStatic(MolangExpression.constant(1), MolangExpression.constant(1),
                MolangExpression.constant(1), null);
    }

    private static ParticleTintingGradient parseGradientColor(JsonObject obj, ParticleMolangEnvironment molang) {
        MolangExpression interpolant = molang.compile(getMolang(obj, "interpolant", "0"));
        JsonElement gradientElem = obj.get("gradient");

        if (gradientElem == null) {
            return new ParticleTintingGradient(interpolant, new float[]{0},
                    new MolangExpression[][]{{MolangExpression.constant(1), MolangExpression.constant(1),
                            MolangExpression.constant(1), MolangExpression.constant(1)}});
        }

        List<Float> stopList = new ArrayList<>();
        List<MolangExpression[]> colorList = new ArrayList<>();

        if (gradientElem.isJsonObject()) {
            JsonObject gradient = gradientElem.getAsJsonObject();
            List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(gradient.entrySet());
            entries.sort(Comparator.comparingDouble(a -> Double.parseDouble(a.getKey())));
            for (Map.Entry<String, JsonElement> entry : entries) {
                stopList.add(Float.parseFloat(entry.getKey()));
                colorList.add(parseColorField(entry.getValue(), molang));
            }
        } else if (gradientElem.isJsonArray()) {
            JsonArray arr = gradientElem.getAsJsonArray();
            int size = arr.size();
            for (int i = 0; i < size; i++) {
                stopList.add(size > 1 ? (float) i / (size - 1) : 0f);
                colorList.add(parseColorField(arr.get(i), molang));
            }
        }

        float[] stops = new float[stopList.size()];
        for (int i = 0; i < stopList.size(); i++) stops[i] = stopList.get(i);
        return new ParticleTintingGradient(interpolant, stops, colorList.toArray(new MolangExpression[0][]));
    }

    private static MolangExpression[] parseColorField(JsonElement elem, ParticleMolangEnvironment molang) {
        if (elem.isJsonPrimitive() && elem.getAsJsonPrimitive().isString()) {
            float[] rgba = parseHexColor(elem.getAsString());
            return new MolangExpression[]{
                    MolangExpression.constant(rgba[0]), MolangExpression.constant(rgba[1]),
                    MolangExpression.constant(rgba[2]), MolangExpression.constant(rgba[3])};
        }
        if (elem.isJsonArray()) {
            JsonArray arr = elem.getAsJsonArray();
            return new MolangExpression[]{
                    molang.compile(molangFromElement(arr.get(0), "1")),
                    molang.compile(molangFromElement(arr.get(1), "1")),
                    molang.compile(molangFromElement(arr.get(2), "1")),
                    arr.size() > 3 ? molang.compile(molangFromElement(arr.get(3), "1")) : MolangExpression.constant(1)};
        }
        return new MolangExpression[]{
                MolangExpression.constant(1), MolangExpression.constant(1),
                MolangExpression.constant(1), MolangExpression.constant(1)};
    }
}
