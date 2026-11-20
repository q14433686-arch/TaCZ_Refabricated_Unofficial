// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.motion;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponent;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponentDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolang;
import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolangArray3;

/**
 * 参数化运动。对应 "minecraft:particle_motion_parametric"。
 */
public record ParticleMotionParametric(
        @Nullable MolangExpression[] relativePosition,
        @Nullable MolangExpression[] direction,
        @Nullable MolangExpression rotation
) implements IParticleComponentDefinition, IParticleComponent {

    @Override public int order() { return 300; }
    @Override public boolean requireUpdate() { return true; }

    @Override
    public void apply(ParticleInstance p) { /* 首次由 update 处理 */ }

    @Override
    public void update(ParticleInstance p) {
        if (p.emitter == null) return;
        applyLegacy(p, p.emitter.getMolang().getContext(), p.emitter.getDt());
    }

    /** 兼容旧调用方（Phase 4 后移除） */
    public void applyLegacy(ParticleInstance p, MolangContext<?> ctx, float dt) {
        if (relativePosition != null) {
            var position=new org.joml.Vector3f(p.originX+(float) relativePosition[0].evaluate(ctx),
                    p.originY+(float) relativePosition[1].evaluate(ctx),p.originZ+(float) relativePosition[2].evaluate(ctx));
            if(p.worldSpace)p.emitter.getWorldTransform().transformPosition(position);
            else if(p.fpDetached)p.emitter.getEmitterTransform().transformPosition(position);
            p.x=position.x;p.y=position.y;p.z=position.z;
        }
        if(direction!=null){
            var vector=new org.joml.Vector3f((float)direction[0].evaluate(ctx),(float)direction[1].evaluate(ctx),(float)direction[2].evaluate(ctx));
            if(p.worldSpace)p.emitter.getWorldTransform().transformDirection(vector);
            else if(p.fpDetached)p.emitter.getEmitterTransform().transformDirection(vector);
            p.vx=vector.x;p.vy=vector.y;p.vz=vector.z;
        }
        if (rotation != null) {
            p.rotation = (float) rotation.evaluate(ctx);
        }
    }

    public static ParticleMotionParametric fromJson(String key, JsonElement value, ParticleMolangEnvironment molang) {
        JsonObject obj = value.getAsJsonObject();
        return new ParticleMotionParametric(
                obj.has("relative_position")
                        ? compileArray3(molang, getMolangArray3(obj, "relative_position", "0", "0", "0")) : null,
                obj.has("direction")
                        ? compileArray3(molang, getMolangArray3(obj, "direction", "0", "0", "0")) : null,
                obj.has("rotation") ? molang.compile(getMolang(obj, "rotation", "0")) : null);
    }

    private static MolangExpression[] compileArray3(ParticleMolangEnvironment molang, String[] exprs) {
        return new MolangExpression[]{molang.compile(exprs[0]), molang.compile(exprs[1]), molang.compile(exprs[2])};
    }
}
