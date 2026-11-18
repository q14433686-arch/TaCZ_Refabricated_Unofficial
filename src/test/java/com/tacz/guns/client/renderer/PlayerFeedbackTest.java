package com.tacz.guns.client.renderer;

import com.tacz.guns.client.renderer.other.GunHurtBobTweak;
import com.tacz.guns.network.message.ServerMessageBulletHitBlock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerFeedbackTest {
    @Test void heldGunTiltHasDirectionalWeightAndZeroAndFullControls() {
        assertEquals(1f, GunHurtBobTweak.heldGunTilt(10,0,.1), .0001f);
        assertEquals(.5f, GunHurtBobTweak.heldGunTilt(10,90,.1), .0001f);
        assertEquals(.5f, GunHurtBobTweak.heldGunTilt(10,-90,.1), .0001f);
        assertEquals(1f, GunHurtBobTweak.heldGunTilt(10,180,.1), .0001f);
        assertEquals(0f, GunHurtBobTweak.heldGunTilt(10,45,0));
        assertEquals(-10f, GunHurtBobTweak.heldGunTilt(-10,0,1), .0001f);
    }
    @Test void impactSendRangeIncludesBoundaryShooterAndUnlimitedWithoutIntOverflow() {
        assertTrue(ServerMessageBulletHitBlock.shouldSend(128,false,128*128));
        assertFalse(ServerMessageBulletHitBlock.shouldSend(128,false,128*128+.01));
        assertTrue(ServerMessageBulletHitBlock.shouldSend(1,true,1_000_000));
        assertTrue(ServerMessageBulletHitBlock.shouldSend(0,false,1e20));
        assertTrue(ServerMessageBulletHitBlock.shouldSend(Integer.MAX_VALUE,false,1e15));
    }
}
