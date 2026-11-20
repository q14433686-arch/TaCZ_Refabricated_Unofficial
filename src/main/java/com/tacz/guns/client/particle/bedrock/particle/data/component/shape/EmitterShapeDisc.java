// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.component.shape;

import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangContext;
import com.tacz.guns.client.particle.bedrock.molang.runtime.MolangExpression;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import org.jetbrains.annotations.Nullable;
import java.util.Random;

public record EmitterShapeDisc(MolangExpression[] offset, MolangExpression radius, PlaneNormal planeNormal,
                                boolean surfaceOnly, @Nullable MolangExpression[] direction,
                                DirectionMode directionMode) implements EmitterShape {
    @Override
    public void applyPosition(ParticleInstance p, MolangContext<?> ctx, Random random) {
        float ox = (float) offset[0].evaluate(ctx);
        float oy = (float) offset[1].evaluate(ctx);
        float oz = (float) offset[2].evaluate(ctx);
        float r = (float) radius.evaluate(ctx);
        float angle = (float) (random.nextFloat() * Math.PI * 2);
        float dist = surfaceOnly ? r : r * (float) Math.sqrt(random.nextFloat());
        float lx = dist * (float) Math.cos(angle);
        float lz = dist * (float) Math.sin(angle);
        switch (planeNormal) {
            case Y -> { p.x = ox + lx; p.y = oy; p.z = oz + lz; }
            case X -> { p.x = ox; p.y = oy + lx; p.z = oz + lz; }
            case Z -> { p.x = ox + lx; p.y = oy + lz; p.z = oz; }
            default -> { p.x = ox + lx; p.y = oy; p.z = oz + lz; }
        }
    }
}
