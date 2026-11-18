package com.tacz.guns.client.model;

import com.tacz.guns.client.model.listener.constraint.*;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConstraintMagnitudeTest {
    @Test void signedChannelsBlendByMagnitudeAndReset() {
        var c=new ConstraintObject(); c.node=new BedrockPart("constraint");
        var r=new ConstraintRotateListener(c); var t=new ConstraintTranslateListener(c);
        for (int sign : new int[]{1,-1}) {
            float d=(float)Math.toRadians(sign);
            r.update(new float[]{d,-2*d,0},false); r.update(new float[]{d/2,3*d,d},true);
            assertArrayEquals(new float[]{1,3,1},new float[]{c.rotationConstraint.x,c.rotationConstraint.y,c.rotationConstraint.z},1e-4f);
            t.update(new float[]{sign,-sign/2f,0},false); t.update(new float[]{0,-sign,sign/8f},true);
            assertArrayEquals(new float[]{16,16,2},new float[]{c.translationConstraint.x,c.translationConstraint.y,c.translationConstraint.z},1e-5f);
            r.update(r.initialValue(),false); t.update(t.initialValue(),false);
            assertEquals(0,c.rotationConstraint.length(),1e-4f); assertEquals(0,c.translationConstraint.length(),1e-4f);
        }
    }
}
