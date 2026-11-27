package com.tacz.guns.client.render.scope;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScopeZoomTest {
    @Test void splitWorldAndLensKeepRequestedTotalAtEveryProgress() {
        for(float zoom:new float[]{2,5,8,10,16}) for(double share:new double[]{0,.25,.5,1}) {
            float world=(float)Math.pow(zoom,share);
            for(int step=0;step<=100;step++) {
                float p=step/100f, requested=1+(zoom-1)*p, worldNow=1+(world-1)*p;
                assertEquals(requested,worldNow*ScopePipRenderState.splitLensZoom(zoom,world,p),.00001f);
            }
        }
    }
    @Test void endpointsAndClampedProgressAreExact() {
        assertEquals(1,ScopePipRenderState.splitLensZoom(8,2,-1));
        assertEquals(4,ScopePipRenderState.splitLensZoom(8,2,2));
        assertEquals(1,ScopePipRenderState.splitLensZoom(8,8,.37f));
    }
}
