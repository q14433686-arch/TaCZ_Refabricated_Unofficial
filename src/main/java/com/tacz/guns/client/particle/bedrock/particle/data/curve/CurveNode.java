// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.curve;

/**
 * Bezier Chain 曲线节点。
 *
 * @param value 节点值
 * @param slope 节点斜率
 */
public record CurveNode(float value, float slope) {
}
