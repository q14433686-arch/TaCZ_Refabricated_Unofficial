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
        if(!(ctx.getEntity() instanceof net.minecraft.world.entity.Entity entity))
            throw new IllegalStateException("entity_aabb requires a bound emitter owner");
        var box=entity.getBoundingBox();
        float hx=(float)box.getXsize()/2,hy=(float)box.getYsize()/2,hz=(float)box.getZsize()/2;
        float x=random.nextFloat()*2-1,y=random.nextFloat()*2-1,z=random.nextFloat()*2-1;
        if(surfaceOnly){switch(random.nextInt(3)){case 0->x=random.nextBoolean()?1:-1;case 1->y=random.nextBoolean()?1:-1;case 2->z=random.nextBoolean()?1:-1;}}
        p.x=ox+x*hx;p.y=oy+hy+y*hy;p.z=oz+z*hz;
    }
}
