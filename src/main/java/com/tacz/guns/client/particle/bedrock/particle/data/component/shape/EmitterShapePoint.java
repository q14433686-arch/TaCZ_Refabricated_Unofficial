// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.shape;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;
import java.util.Random;

public record EmitterShapePoint(MolangExpression[] offset, @Nullable MolangExpression[] direction,
                                 DirectionMode directionMode) implements EmitterShape {
    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        p.x = (float) offset[0].evaluate(ctx);
        p.y = (float) offset[1].evaluate(ctx);
        p.z = (float) offset[2].evaluate(ctx);
    }
}
