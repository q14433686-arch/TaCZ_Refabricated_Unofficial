// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.shape;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;
import java.util.Random;

public record EmitterShapeSphere(MolangExpression[] offset, MolangExpression radius, boolean surfaceOnly,
                                  @Nullable MolangExpression[] direction,
                                  DirectionMode directionMode) implements EmitterShape {
    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        float ox = (float) offset[0].evaluate(ctx);
        float oy = (float) offset[1].evaluate(ctx);
        float oz = (float) offset[2].evaluate(ctx);
        float r = (float) radius.evaluate(ctx);
        float theta = (float) (random.nextFloat() * Math.PI * 2);
        float phi = (float) (Math.acos(2 * random.nextFloat() - 1));
        float dist = surfaceOnly ? r : r * (float) Math.cbrt(random.nextFloat());
        p.x = ox + dist * (float) (Math.sin(phi) * Math.cos(theta));
        p.y = oy + dist * (float) Math.cos(phi);
        p.z = oz + dist * (float) (Math.sin(phi) * Math.sin(theta));
    }
}
