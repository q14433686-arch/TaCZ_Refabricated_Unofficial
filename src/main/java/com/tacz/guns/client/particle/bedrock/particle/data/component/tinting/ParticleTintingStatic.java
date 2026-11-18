// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.tinting;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponent;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponentDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;

public record ParticleTintingStatic(MolangExpression r, MolangExpression g, MolangExpression b,
                                     @Nullable MolangExpression a)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override public int order() { return 210; }
    @Override public boolean requireUpdate() { return false; }

    @Override
    public void apply(ParticleInstance p) {
        if (p.emitter == null) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        p.r = (float) r.evaluate(ctx);
        p.g = (float) g.evaluate(ctx);
        p.b = (float) b.evaluate(ctx);
        p.a = a != null ? (float) a.evaluate(ctx) : 1f;
    }
}
