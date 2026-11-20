// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.tinting;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponent;
import com.tacz.guns.client.particle.bedrock.particle.data.component.IParticleComponentDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;

public record ParticleTintingGradient(MolangExpression interpolant, float[] stops,
                                       MolangExpression[][] colors)
        implements IParticleComponentDefinition, IParticleComponent {

    @Override public int order() { return 210; }
    @Override public boolean requireUpdate() { return true; }

    @Override
    public void apply(ParticleInstance p) {
        updateColor(p);
    }

    @Override
    public void update(ParticleInstance p) {
        updateColor(p);
    }

    private void updateColor(ParticleInstance p) {
        if (p.emitter == null || colors.length == 0) return;
        MolangContext<?> ctx = p.emitter.getMolang().getContext();
        float t = (float) interpolant.evaluate(ctx);

        if (colors.length == 1) {
            p.r = (float) colors[0][0].evaluate(ctx);
            p.g = (float) colors[0][1].evaluate(ctx);
            p.b = (float) colors[0][2].evaluate(ctx);
            p.a = (float) colors[0][3].evaluate(ctx);
            return;
        }

        t = Math.max(stops[0], Math.min(stops[stops.length - 1], t));
        int idx = 0;
        for (int i = 0; i < stops.length - 1; i++) {
            if (t >= stops[i]) idx = i;
        }
        if (idx >= colors.length - 1) idx = colors.length - 2;

        float segStart = stops[idx], segEnd = stops[idx + 1];
        float frac = segEnd > segStart ? (t - segStart) / (segEnd - segStart) : 0;
        frac = Math.max(0, Math.min(1, frac));

        MolangExpression[] c0 = colors[idx], c1 = colors[idx + 1];
        p.r = lerp((float) c0[0].evaluate(ctx), (float) c1[0].evaluate(ctx), frac);
        p.g = lerp((float) c0[1].evaluate(ctx), (float) c1[1].evaluate(ctx), frac);
        p.b = lerp((float) c0[2].evaluate(ctx), (float) c1[2].evaluate(ctx), frac);
        p.a = lerp((float) c0[3].evaluate(ctx), (float) c1[3].evaluate(ctx), frac);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
}
