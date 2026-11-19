package com.tacz.guns.client.render.light;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpillFieldTest {
    @Test void muzzlePeaksAtSourceAndReachesForwardOffAxis() {
        // Keep arithmetic in emitter space even at a distant world coordinate.
        var s=new Vec3(2.5e7,70,-2.5e7);
        var f=new SpillField(s,new Vec3(0,0,SpillField.MUZZLE_BIAS),4.2);
        assertEquals(1f,f.gain(s.x,s.y,s.z));
        assertTrue(f.gain(s.x+1.8,s.y-1.5,s.z+4)>.15);
        assertEquals(0f,f.gain(s.x,s.y,s.z-4));
        assertEquals(0f,f.gain(s.x,s.y,s.z+7.51));
        assertEquals(s.z+7.5,f.boundingCenter().z+f.boundingRadius(),1e-7);
    }
    @Test void blastIsRadialAndFallsSmoothlyToZero() {
        var f=new SpillField(Vec3.ZERO,Vec3.ZERO,8.5);
        assertEquals(Vec3.ZERO,f.boundingCenter());
        assertEquals(8.5,f.boundingRadius());
        assertEquals(f.gain(0,-5,0),f.gain(3,0,4));
        assertTrue(f.gain(3,0,4)>.35f);
        assertTrue(f.gain(8.4,0,0)<.001f);
        assertEquals(0f,f.gain(9,0,0));
    }
}
