// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.shape;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;
import java.util.Random;

public record EmitterShapeBox(MolangExpression[] offset, MolangExpression[] halfDimensions, boolean surfaceOnly,
                               @Nullable MolangExpression[] direction,
                               DirectionMode directionMode) implements EmitterShape {
    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        float ox = (float) offset[0].evaluate(ctx);
        float oy = (float) offset[1].evaluate(ctx);
        float oz = (float) offset[2].evaluate(ctx);
        float hx = (float) halfDimensions[0].evaluate(ctx);
        float hy = (float) halfDimensions[1].evaluate(ctx);
        float hz = (float) halfDimensions[2].evaluate(ctx);
        if (surfaceOnly) {
            int face = random.nextInt(6);
            float u = random.nextFloat() * 2 - 1, v = random.nextFloat() * 2 - 1;
            switch (face) {
                case 0 -> { p.x = ox + hx; p.y = oy + u * hy; p.z = oz + v * hz; }
                case 1 -> { p.x = ox - hx; p.y = oy + u * hy; p.z = oz + v * hz; }
                case 2 -> { p.y = oy + hy; p.x = ox + u * hx; p.z = oz + v * hz; }
                case 3 -> { p.y = oy - hy; p.x = ox + u * hx; p.z = oz + v * hz; }
                case 4 -> { p.z = oz + hz; p.x = ox + u * hx; p.y = oy + v * hy; }
                case 5 -> { p.z = oz - hz; p.x = ox + u * hx; p.y = oy + v * hy; }
            }
        } else {
            p.x = ox + (random.nextFloat() * 2 - 1) * hx;
            p.y = oy + (random.nextFloat() * 2 - 1) * hy;
            p.z = oz + (random.nextFloat() * 2 - 1) * hz;
        }
    }
}
