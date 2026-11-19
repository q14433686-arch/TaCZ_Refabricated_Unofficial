// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.curve;

/**
 * 曲线插值类型。
 */
public enum CurveType {
    LINEAR,
    BEZIER,
    CATMULL_ROM,
    BEZIER_CHAIN;

    public static CurveType fromString(String str) {
        return switch (str.toLowerCase()) {
            case "bezier" -> BEZIER;
            case "catmull_rom" -> CATMULL_ROM;
            case "bezier_chain" -> BEZIER_CHAIN;
            default -> LINEAR;
        };
    }
}
