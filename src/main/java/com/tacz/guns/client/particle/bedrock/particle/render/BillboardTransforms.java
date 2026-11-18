// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.render;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** CPU transforms for billboards when Minecraft splits the view between ModelView and PoseStack. */
public final class BillboardTransforms {
    private BillboardTransforms() {}

    /** Combines both transforms for billboard centers and directions computed in view space. */
    public static Matrix4f viewPose(Matrix4fc modelView, Matrix4fc pose) {
        return new Matrix4f(modelView).mul(pose);
    }

    /** Converts view-space vertices back to the space consumed by the shader's ModelView. */
    public static Matrix4f viewToRender(Matrix4fc modelView) {
        return new Matrix4f(modelView).invert();
    }

    /** Preserves a local quad's transformed center while making its axes unit-length in view space. */
    public static Matrix4f billboardPose(Matrix4fc modelView, Matrix4fc effectivePose, Vector3fc particlePosition) {
        Vector3f center = effectivePose.transformPosition(particlePosition, new Vector3f());
        Matrix4f billboard = viewToRender(modelView);
        Vector3f localCenter = billboard.transformDirection(particlePosition, new Vector3f());
        return billboard.setTranslation(center.sub(localCenter));
    }
}
