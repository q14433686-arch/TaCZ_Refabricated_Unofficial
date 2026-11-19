// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.curve;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolang;
import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.molangFromElement;

/**
 * 粒子曲线定义。对应 JSON 中 {@code particle_effect.curves} 下的每条曲线。
 * <p>
 * 曲线将 Molang 输入值映射为输出值，结果写入 variable 命名空间供其他表达式引用。
 *
 * @param type            曲线插值类型
 * @param input           输入值（Molang 表达式）
 * @param horizontalRange 水平范围（Molang 表达式），输入值会除以此值归一化到 [0,1]
 * @param nodes           节点值数组（LINEAR/BEZIER/CATMULL_ROM 使用）
 * @param chainNodes      Bezier Chain 节点数组（BEZIER_CHAIN 使用）
 */
public record ParticleCurve(
        CurveType type,
        MolangExpression input,
        MolangExpression horizontalRange,
        float[] nodes,
        @Nullable CurveNode[] chainNodes
) {

    /**
     * 从 JSON 解析单条曲线定义。
     *
     * @param obj    曲线 JSON 对象
     * @param molang Molang 编译环境
     * @return 解析后的曲线定义
     */
    public static ParticleCurve fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        CurveType type = CurveType.LINEAR;
        if (obj.has("type")) {
            type = CurveType.fromString(obj.get("type").getAsString());
        }

        MolangExpression input = molang.compile(getMolang(obj, "input", "0"));
        MolangExpression horizontalRange = molang.compile(getMolang(obj, "horizontal_range", "1"));

        float[] nodes = null;
        CurveNode[] chainNodes = null;

        if (obj.has("nodes")) {
            JsonElement nodesElem = obj.get("nodes");
            if (type == CurveType.BEZIER_CHAIN && nodesElem.isJsonObject()) {
                // Bezier Chain: nodes 是对象，每个 key 是位置，value 是 {value, slope}
                JsonObject nodesObj = nodesElem.getAsJsonObject();
                List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(nodesObj.entrySet());
                entries.sort(Comparator.comparingDouble(entry -> Double.parseDouble(entry.getKey())));
                chainNodes = new CurveNode[entries.size()];
                nodes = new float[entries.size()]; // 存储位置 key
                int i = 0;
                for (Map.Entry<String, JsonElement> entry : entries) {
                    nodes[i] = Float.parseFloat(entry.getKey());
                    JsonElement val = entry.getValue();
                    if (val.isJsonObject()) {
                        JsonObject nodeObj = val.getAsJsonObject();
                        float value = nodeObj.has("value") ? nodeObj.get("value").getAsFloat() : 1f;
                        float slope = nodeObj.has("slope") ? nodeObj.get("slope").getAsFloat() : 1f;
                        chainNodes[i] = new CurveNode(value, slope);
                    } else {
                        chainNodes[i] = new CurveNode(1f, 1f);
                    }
                    i++;
                }
            } else if (nodesElem.isJsonArray()) {
                // LINEAR/BEZIER/CATMULL_ROM: nodes 是数组
                JsonArray arr = nodesElem.getAsJsonArray();
                nodes = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    nodes[i] = Float.parseFloat(molangFromElement(arr.get(i), "0"));
                }
            } else if (nodesElem.isJsonObject()) {
                // 对象格式的普通节点（带 key 的 CurveNode）
                JsonObject nodesObj = nodesElem.getAsJsonObject();
                List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(nodesObj.entrySet());
                entries.sort(Comparator.comparingDouble(entry -> Double.parseDouble(entry.getKey())));
                chainNodes = new CurveNode[entries.size()];
                nodes = new float[entries.size()];
                int i = 0;
                for (Map.Entry<String, JsonElement> entry : entries) {
                    nodes[i] = Float.parseFloat(entry.getKey());
                    JsonElement val = entry.getValue();
                    if (val.isJsonObject()) {
                        JsonObject nodeObj = val.getAsJsonObject();
                        chainNodes[i] = new CurveNode(
                                nodeObj.has("value") ? nodeObj.get("value").getAsFloat() : 1f,
                                nodeObj.has("slope") ? nodeObj.get("slope").getAsFloat() : 1f);
                    } else {
                        chainNodes[i] = new CurveNode(val.getAsFloat(), 1f);
                    }
                    i++;
                }
            }
        }

        if (nodes == null) nodes = new float[0];
        return new ParticleCurve(type, input, horizontalRange, nodes, chainNodes);
    }
}
