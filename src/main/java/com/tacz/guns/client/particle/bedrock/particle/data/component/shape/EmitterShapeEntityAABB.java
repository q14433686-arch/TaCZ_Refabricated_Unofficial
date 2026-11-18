// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.shape;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;
import java.util.Random;

public record EmitterShapeEntityAABB(MolangExpression[] offset, boolean surfaceOnly,
                                      @Nullable MolangExpression[] direction,
                                      DirectionMode directionMode) implements EmitterShape {
    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        float ox = (float) offset[0].evaluate(ctx);
        float oy = (float) offset[1].evaluate(ctx);
        float oz = (float) offset[2].evaluate(ctx);
        p.x = ox + (random.nextFloat() * 2 - 1) * 0.5f;
        p.y = oy + random.nextFloat();
        p.z = oz + (random.nextFloat() * 2 - 1) * 0.5f;
    }
}
