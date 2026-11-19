package com.tacz.guns.client.particle;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDeserializer;
import com.tacz.guns.client.particle.bedrock.particle.data.ParticleEffectDefinition;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleEmitterInstance;
import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleMolangEnvironment;
import com.tacz.guns.client.resource.pojo.display.gun.MuzzleFlash;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Compile the shipped expressions once; tick distinct shot environments, not an imitation of the formulas. */
class MuzzleDefinitionTest {
    private static final Gson GSON = new Gson();
    private static MuzzleEffectParameters shot(String part) {
        return MuzzleEffectParameters.resolve(GSON.fromJson("{\"type\":\"renew\",\"initial_speed_scale\":1.5}", MuzzleFlash.class),
                GSON.fromJson(part, AttachmentData.class), false);
    }
    private static ParticleEffectDefinition smokeDefinition() throws IOException {
        var path = Path.of("src/main/resources/assets/tacz/custom/tacz_default_gun/assets/tacz/particle_definitions/muzzle_flash_smoke.particle.json");
        return new ParticleEffectDeserializer().parse(JsonParser.parseString(Files.readString(path)), new ParticleMolangEnvironment());
    }
    @Test void smokeDefaultsAndIndependentModifiersReachParticles() throws IOException {
        var def = smokeDefinition();
        var tuned = shot("""
                {"flash_hider":0,"smoke_hider":0.4,"smoke_count_scale":8,"smoke_speed_scale":2.5,
                 "smoke_directions":{"plane_angle":90,"out_angle":0},"smoke_spread_angle":0}
                """);
        assertFalse(tuned.hasFlash());
        for (boolean changed : new boolean[]{true,false}) {
            var effect = changed ? tuned : shot("{}");
            assertTrue(effect.smokeEnabled());
            var env = new ParticleMolangEnvironment(); effect.bindSmoke(env);
            var emitter = new ParticleEmitterInstance(def, env);
            emitter.disableFPMode(); emitter.setEmitterTransform(new Matrix4f(), new Matrix4f());
            emitter.tick(.05f);
            int n = emitter.getParticleCount();
            // Random rate is [25%,100%] of 120 or 15: non-overlapping intervals.
            assertTrue(changed ? n >= 30 && n <= 120 : n >= 3 && n <= 15, "count=" + n);
            for (var p : emitter.getParticles()) {
                assertEquals((changed ? .4f : 1f)*.1f*(.8f+.4f*p.random2), p.width, .0001f);
                assertEquals(p.width, p.height, .0001f);
                assertEquals(changed ? 22.5 : 9, Math.sqrt(p.vx*p.vx+p.vy*p.vy+p.vz*p.vz), .001);
                if (changed) assertEquals(-22.5f, p.vy, .001f); // Author up, corrected once; zero-width cone.
                assertTrue(p.worldSpace);
            }
        }
        for (String field : new String[]{"smoke_hider","smoke_speed_scale","smoke_count_scale"}) {
            var disabled = shot("{\""+field+"\":0}");
            assertFalse(disabled.smokeEnabled(), field);
            assertTrue(disabled.hasFlash());
        }
    }

    @Test void nonzeroConeUsesPerParticleAxisAndBothTangentVectors() throws IOException {
        var effect = shot("""
                {"smoke_directions":[{"plane_angle":0,"out_angle":90},{"plane_angle":90,"out_angle":0}],
                 "smoke_spread_angle":40,"smoke_count_scale":8}
                """);
        var env = new ParticleMolangEnvironment(); effect.bindSmoke(env);
        var emitter = new ParticleEmitterInstance(smokeDefinition(),env); emitter.disableFPMode(); emitter.tick(.05f);
        assertTrue(emitter.getParticleCount()>=30);
        for (var p : emitter.getParticles()) {
            double axial = 1 - p.random3 * (1-Math.cos(Math.toRadians(40)));
            double radial = Math.sqrt(1-axial*axial), theta = 2*Math.PI*p.random4;
            double u = radial*Math.cos(theta), v = radial*Math.sin(theta);
            boolean forward = p.random1 < .5f;
            // For -Z, basis +X/-Y. For -Y, basis +X/+Z. Test actual compiled
            // direction and speed, not merely that three variable names exist in storage.
            assertEquals(9*u,p.vx, .001);
            assertEquals(9*(forward ? -v : -axial),p.vy,.001);
            assertEquals(9*(forward ? -axial : v),p.vz,.001);
        }
    }

    @Test void modelLightPreservesSkyAndDoesNotDarkenABrightScene() {
        int sky = 6 << 20;
        assertEquals(sky | 10 << 4, NativeMuzzleEffects.withFlashLight(sky,15));
        assertEquals(sky | 12 << 4, NativeMuzzleEffects.withFlashLight(sky | 5 << 4,15));
        assertEquals(sky | 14 << 4, NativeMuzzleEffects.withFlashLight(sky | 14 << 4,3));
        assertEquals(sky | 3 << 4, NativeMuzzleEffects.withFlashLight(sky,3));
    }
}
