package com.tacz.guns.client.resource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.serialize.IdentifierSerializer;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MuzzleEffectConfigTest {
    private final Gson gson = new GsonBuilder().registerTypeAdapter(Identifier.class, new IdentifierSerializer()).create();

    @Test void legacyAndRenewAreDistinctAndExplicitNullDisablesOnlyOneLayer() {
        var legacy = gson.fromJson("{}", MuzzleFlash.class);
        assertFalse(legacy.isRenew());
        var renew = gson.fromJson("{\"type\":\"renew\",\"first_person_flash\":null}", MuzzleFlash.class);
        assertTrue(renew.isRenew());
        assertNull(renew.getTexture()); // Glock-style configuration must not require a legacy quad.
        assertNull(renew.getFirstPersonFlashId());
        assertEquals(Identifier.parse("tacz:muzzle_flash_smoke"), renew.getFirstPersonSmokeId());
        assertEquals(Identifier.parse("tacz:muzzle_flash"), renew.getThirdPersonFlashId());
        assertEquals(Identifier.parse("tacz:muzzle_flash_smoke"), renew.getThirdPersonSmokeId());
    }

    @Test void smokeSizeIsIndependentOfFlashAndLightIsBounded() {
        var effect = gson.fromJson("{\"scale\":0.2,\"smoke_scale\":2,\"flash_light_level\":99}", MuzzleFlash.class);
        assertEquals(.2f, effect.getScale());
        assertEquals(2f, effect.getSmokeScale());
        assertEquals(15, effect.getFlashLightLevel());
        assertEquals(0, gson.fromJson("{\"flash_light_level\":-9}", MuzzleFlash.class).getFlashLightLevel());
    }

    @Test void attachmentZeroIsNotMissingAndDirectionsAreNormalized() {
        var part = gson.fromJson("{\"flash_hider\":0.2,\"smoke_hider\":0,\"smoke_speed_scale\":2,\"smoke_count_scale\":0,\"smoke_direction\":[0,4,0]}", AttachmentData.class);
        assertTrue(part.hasFlashHider());
        assertEquals(.2f, part.getFlashHider());
        assertTrue(part.hasSmokeHider());
        assertEquals(0f, part.getSmokeHider());
        assertEquals(2f, part.getSmokeSpeedScale());
        assertEquals(0f, part.getSmokeCountScale());
        assertArrayEquals(new float[]{0,1,0}, part.getSmokeDirections()[0], .0001f);
        var missing = gson.fromJson("{}", AttachmentData.class);
        assertFalse(missing.hasFlashHider());
        assertEquals(1f, missing.getFlashHider());
        assertNull(missing.getSmokeDirectionConfig());
    }

    @Test void malformedDirectionsDoNotInvalidateTheGunOrAttachment() {
        for (String direction : new String[]{"null", "true", "\"bad\"", "[0,0,0]", "[1,2]"}) {
            var gun = gson.fromJson("{\"smoke_direction\":" + direction + "}", MuzzleFlash.class);
            assertNull(gun.getSmokeDirectionConfig());
            var attachment = gson.fromJson("{\"smoke_direction\":" + direction + "}", AttachmentData.class);
            assertNull(attachment.getSmokeDirectionConfig());
        }
    }

    @Test void resolvedSilencerKeepsSmokeAndAttachmentOverridesGunDirection() {
        var flash = gson.fromJson("{\"type\":\"renew\",\"smoke_direction\":[0,0,-1]}", MuzzleFlash.class);
        var silent = com.tacz.guns.client.particle.MuzzleEffectParameters.resolve(flash, null, true);
        assertEquals(0f, silent.flashScale());
        assertNotNull(silent.firstSmoke());
        var part = gson.fromJson("{\"flash_hider\":0.2,\"smoke_hider\":2,\"smoke_count_scale\":2,\"smoke_direction\":[0,1,0]}", AttachmentData.class);
        var resolved = com.tacz.guns.client.particle.MuzzleEffectParameters.resolve(flash, part, true);
        assertEquals(.2f, resolved.flashScale());
        assertEquals(2f, resolved.smokeSize()); // Default size is 1, not the absent-field sentinel 0.
        assertEquals(30f, resolved.smokeCount()); // Default definition count is 15.
        assertEquals(3, resolved.flashLight());
        assertArrayEquals(new float[]{0,-1,0}, resolved.smokeDirection().directions()[0], .0001f);
        var bare = com.tacz.guns.client.particle.MuzzleEffectParameters.resolve(flash, null, false);
        assertEquals(0f, bare.smokeSize()); // No multiplier: retain definition's default sentinel.
        assertArrayEquals(new float[]{0,0,-1}, bare.smokeDirection().directions()[0], .0001f);
        assertNull(com.tacz.guns.client.particle.MuzzleEffectParameters.resolve(gson.fromJson("{}", MuzzleFlash.class), null, false));
    }
}
