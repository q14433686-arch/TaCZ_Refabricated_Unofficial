// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.*;

public record ParticleAppearanceBillboard(
        MolangExpression[] size,
        FaceCameraMode faceCameraMode,
        @Nullable UVConfig uv,
        @Nullable FlipbookConfig flipbook,
        boolean dynamicSize
) implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 200;
    }

    @Override
    public boolean requireUpdate() {
        return flipbook != null || dynamicSize;
    }

    @Override
    public void apply(ParticleInstance p) {
        updateAppearance(p);
    }

    @Override
    public void update(ParticleInstance p) {
        updateAppearance(p);
    }

    private void updateAppearance(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        p.width = (float) size[0].evaluate(ctx);
        p.height = (float) size[1].evaluate(ctx);

        if (flipbook != null) {
            var fb = flipbook;
            int maxFrame = (int) fb.maxFrame().evaluate(ctx);
            float frame = fb.stretchToLifetime() && p.maxLifetime > 0
                    ? (p.age / p.maxLifetime) * maxFrame
                    : p.age * fb.framesPerSecond();
            int frameIdx = Math.max(0, fb.loop() && maxFrame > 0
                    ? ((int) frame) % maxFrame
                    : Math.min((int) frame, maxFrame - 1));

            float baseU = (float) fb.baseUV()[0].evaluate(ctx);
            float baseV = (float) fb.baseUV()[1].evaluate(ctx);
            float sizeU = (float) fb.sizeUV()[0].evaluate(ctx);
            float sizeV = (float) fb.sizeUV()[1].evaluate(ctx);
            float stepU = (float) fb.stepUV()[0].evaluate(ctx);
            float stepV = (float) fb.stepUV()[1].evaluate(ctx);
            float u = baseU + stepU * frameIdx;
            float v = baseV + stepV * frameIdx;
            p.u0 = u / fb.textureWidth();
            p.v0 = v / fb.textureHeight();
            p.u1 = (u + sizeU) / fb.textureWidth();
            p.v1 = (v + sizeV) / fb.textureHeight();
        } else if (uv != null) {
            p.u0 = (float) uv.u().evaluate(ctx) / uv.textureWidth();
            p.v0 = (float) uv.v().evaluate(ctx) / uv.textureHeight();
            p.u1 = ((float) uv.u().evaluate(ctx) + (float) uv.width().evaluate(ctx)) / uv.textureWidth();
            p.v1 = ((float) uv.v().evaluate(ctx) + (float) uv.height().evaluate(ctx)) / uv.textureHeight();
        }
    }

    public enum FaceCameraMode {
        ROTATE_XYZ, ROTATE_Y, LOOKAT_XYZ, LOOKAT_Y, LOOKAT_DIRECTION,
        DIRECTION_X, DIRECTION_Y, DIRECTION_Z,
        EMITTER_TRANSFORM_XY, EMITTER_TRANSFORM_XZ, EMITTER_TRANSFORM_YZ
    }

    public record UVConfig(
            MolangExpression u, MolangExpression v,
            MolangExpression width, MolangExpression height,
            int textureWidth, int textureHeight
    ) {}

    public record FlipbookConfig(
            MolangExpression[] baseUV, MolangExpression[] sizeUV, MolangExpression[] stepUV,
            float framesPerSecond, MolangExpression maxFrame,
            boolean stretchToLifetime, boolean loop,
            int textureWidth, int textureHeight
    ) {}

    public static ParticleAppearanceBillboard fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        String[] sizeStr = getMolangArray(obj, "size", 2, "0.1", "0.1");
        MolangExpression[] size = new MolangExpression[]{molang.compile(sizeStr[0]), molang.compile(sizeStr[1])};
        boolean dynamicSize = !isNumericLiteral(sizeStr[0]) || !isNumericLiteral(sizeStr[1]);

        String modeStr = getString(obj, "facing_camera_mode", "rotate_xyz");
        FaceCameraMode mode = parseFaceCameraMode(modeStr);

        JsonObject uvObj = obj.has("uv") ? obj.getAsJsonObject("uv") : null;
        UVConfig uv = null;
        FlipbookConfig flipbook = null;

        if (uvObj != null && uvObj.has("flipbook")) {
            flipbook = parseFlipbookConfig(uvObj, molang);
        } else {
            uv = parseUVConfig(uvObj, molang);
        }

        return new ParticleAppearanceBillboard(size, mode, uv, flipbook, dynamicSize);
    }

    private static FaceCameraMode parseFaceCameraMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "rotate_y" -> FaceCameraMode.ROTATE_Y;
            case "lookat_xyz" -> FaceCameraMode.LOOKAT_XYZ;
            case "lookat_y" -> FaceCameraMode.LOOKAT_Y;
            case "lookat_direction" -> FaceCameraMode.LOOKAT_DIRECTION;
            case "direction_x" -> FaceCameraMode.DIRECTION_X;
            case "direction_y" -> FaceCameraMode.DIRECTION_Y;
            case "direction_z" -> FaceCameraMode.DIRECTION_Z;
            case "emitter_transform_xy" -> FaceCameraMode.EMITTER_TRANSFORM_XY;
            case "emitter_transform_xz" -> FaceCameraMode.EMITTER_TRANSFORM_XZ;
            case "emitter_transform_yz" -> FaceCameraMode.EMITTER_TRANSFORM_YZ;
            default -> FaceCameraMode.ROTATE_XYZ;
        };
    }

    private static UVConfig parseUVConfig(@Nullable JsonObject uvObj, ParticleMolangEnvironment molang) {
        if (uvObj == null) {
            return new UVConfig(MolangExpression.zero(), MolangExpression.zero(),
                    MolangExpression.constant(1), MolangExpression.constant(1), 1, 1);
        }
        int texW = uvObj.has("texture_width") ? uvObj.get("texture_width").getAsInt() : 1;
        int texH = uvObj.has("texture_height") ? uvObj.get("texture_height").getAsInt() : 1;

        if (uvObj.has("uv")) {
            JsonElement uvElem = uvObj.get("uv");
            if (uvElem.isJsonArray()) {
                JsonArray arr = uvElem.getAsJsonArray();
                MolangExpression u = molang.compile(molangFromElement(arr.get(0), "0"));
                MolangExpression v = molang.compile(molangFromElement(arr.get(1), "0"));
                MolangExpression w = MolangExpression.constant(1);
                MolangExpression h = MolangExpression.constant(1);
                if (uvObj.has("uv_size")) {
                    JsonArray sizeArr = uvObj.getAsJsonArray("uv_size");
                    w = molang.compile(molangFromElement(sizeArr.get(0), "1"));
                    h = molang.compile(molangFromElement(sizeArr.get(1), "1"));
                }
                return new UVConfig(u, v, w, h, texW, texH);
            }
        }
        return new UVConfig(MolangExpression.zero(), MolangExpression.zero(),
                MolangExpression.constant(texW), MolangExpression.constant(texH), texW, texH);
    }

    private static FlipbookConfig parseFlipbookConfig(JsonObject uvObj, ParticleMolangEnvironment molang) {
        int texW = uvObj.has("texture_width") ? uvObj.get("texture_width").getAsInt() : 1;
        int texH = uvObj.has("texture_height") ? uvObj.get("texture_height").getAsInt() : 1;
        JsonObject fb = uvObj.getAsJsonObject("flipbook");
        String[] baseStr = getMolangArray(fb, "base_UV", 2, "0", "0");
        String[] sizeStr = getMolangArray(fb, "size_UV", 2, "1", "1");
        String[] stepStr = getMolangArray(fb, "step_UV", 2, "0", "0");
        MolangExpression[] baseUV = new MolangExpression[]{molang.compile(baseStr[0]), molang.compile(baseStr[1])};
        MolangExpression[] sizeUV = new MolangExpression[]{molang.compile(sizeStr[0]), molang.compile(sizeStr[1])};
        MolangExpression[] stepUV = new MolangExpression[]{molang.compile(stepStr[0]), molang.compile(stepStr[1])};
        float fps = fb.has("frames_per_second") ? fb.get("frames_per_second").getAsFloat() : 1;
        MolangExpression maxFrame = molang.compile(getMolang(fb, "max_frame", "1"));
        boolean stretch = getBoolean(fb, "stretch_to_lifetime", false);
        boolean loop = getBoolean(fb, "loop", false);
        return new FlipbookConfig(baseUV, sizeUV, stepUV, fps, maxFrame, stretch, loop, texW, texH);
    }

    /**
     * 检查字符串是否为纯数字字面量（整数或浮点数）。
     */
    private static boolean isNumericLiteral(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
