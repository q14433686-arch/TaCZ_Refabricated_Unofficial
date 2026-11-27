package com.tacz.guns.client.renderer.other;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GunHurtBobTweakTest {
    @Test void ordinaryTiltHasSmoothDirectionalAttenuationAndRetainsSign() {
        for(float angle:new float[]{-14,7})for(double multiplier:new double[]{0,.1,1}) {
            assertEquals(angle*multiplier,GunHurtBobTweak.heldGunTilt(angle,0,multiplier),.00001);
            assertEquals(angle*multiplier,GunHurtBobTweak.heldGunTilt(angle,180,multiplier),.00002);
            assertEquals(angle*multiplier*.5,GunHurtBobTweak.heldGunTilt(angle,90,multiplier),.00001);
            assertEquals(angle*multiplier*.5,GunHurtBobTweak.heldGunTilt(angle,-90,multiplier),.00001);
            assertEquals(angle*multiplier*(1-.5*Math.sqrt(.5)),GunHurtBobTweak.heldGunTilt(angle,45,multiplier),.0002);
        }
    }
}
