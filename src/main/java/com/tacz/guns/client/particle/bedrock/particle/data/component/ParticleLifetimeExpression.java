// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.google.gson.JsonObject;

import org.jetbrains.annotations.Nullable;

import static com.tacz.guns.client.particle.bedrock.particle.data.ParticleJsonUtils.getMolang;

public record ParticleLifetimeExpression(
        MolangExpression maxLifetime,
        @Nullable MolangExpression expirationExpression
) implements IParticleComponentDefinition, IParticleComponent {

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean requireUpdate() {
        return expirationExpression != null;
    }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        p.maxLifetime = (float) maxLifetime.evaluate(p.emitter.getMolang().getContext());
    }

    @Override
    public void update(ParticleInstance p) {
        if (expirationExpression == null || p.emitter == null) return;
        if (expirationExpression.evaluate(p.emitter.getMolang().getContext()) != 0) {
            p.alive = false;
        }
    }

    public static ParticleLifetimeExpression fromJson(JsonObject obj, ParticleMolangEnvironment molang) {
        MolangExpression maxLifetime = molang.compile(getMolang(obj, "max_lifetime", "1"));
        MolangExpression expiration = obj.has("expiration_expression")
                ? molang.compile(getMolang(obj, "expiration_expression", "0")) : null;
        return new ParticleLifetimeExpression(maxLifetime, expiration);
    }
}
