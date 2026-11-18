// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolang;

public record ParticleInitialization(
        @Nullable MolangExpression perRenderExpression,
        @Nullable MolangExpression perUpdateExpression
) implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 360;
    }

    @Override
    public boolean requireUpdate() {
        return perRenderExpression != null || perUpdateExpression != null;
    }

    @Override
    public void update(ParticleInstance p) {
        if (p.emitter == null) return;
        var ctx = p.emitter.getMolang().getContext();
        if (perUpdateExpression != null) perUpdateExpression.evaluate(ctx);
    }

    public static ParticleInitialization fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        MolangExpression perRender = null;
        MolangExpression perUpdate = null;
        if (obj.has("per_render_expression")) {
            String expr = getMolang(obj, "per_render_expression", "");
            if (!expr.isEmpty()) perRender = molang.compile(expr);
        }
        if (obj.has("per_update_expression")) {
            String expr = getMolang(obj, "per_update_expression", "");
            if (!expr.isEmpty()) perUpdate = molang.compile(expr);
        }
        return new ParticleInitialization(perRender, perUpdate);
    }
}
