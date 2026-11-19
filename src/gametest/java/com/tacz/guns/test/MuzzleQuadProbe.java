package com.tacz.guns.test;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

import java.util.IdentityHashMap;

/** Test-only: label frozen snapshots by their actual definition, owner and pass, then measure
 * their four vertices when the deferred billboard callback runs. Smoke/tracers cannot satisfy it. */
public final class MuzzleQuadProbe {
    static final Identifier FLASH = Identifier.parse("tacz:muzzle_flash");
    private static final IdentityHashMap<ParticleInstance, Vector3f[]> corners = new IdentityHashMap<>();
    private static int owner = -1;
    private static boolean hand;
    static Edges last;

    record Edges(float width, float height, float spawnScale) {
        void check(float scale) {
            // Shipped definition: 0.45 * scale on both sides of the center, on both axes.
            if (Math.abs(width / spawnScale - .9f * scale) > .001f || Math.abs(height / spawnScale - .9f * scale) > .001f)
                throw new AssertionError("Wrong flash geometry " + this + " for pack scale " + scale);
        }
    }
    static void begin(int id, boolean first) { owner=id; hand=first; last=null; corners.clear(); }
    static void stop() { begin(-1, false); }

    public static void snapshot(ParticleInstance live, ParticleInstance frozen) {
        if (owner < 0 || live.emitter.isFPMode() != hand || !FLASH.equals(live.emitter.getDefinition().getIdentifier())) return;
        var entity = live.emitter.getMolang().getContext().getEntity();
        if (entity instanceof net.minecraft.world.entity.Entity e && e.getId() == owner)
            corners.put(frozen, new Vector3f[4]);
    }
    public static void vertex(ParticleInstance p, float x, float y, float z) {
        if (owner < 0) return;
        var c = corners.get(p);
        if (c == null) return;
        for (int i=0; i<4; i++) if (c[i] == null) {
            c[i] = new Vector3f(x,y,z);
            if (i == 3) {
                last = new Edges(c[0].distance(c[3]), c[0].distance(c[1]), p.spawnScale);
                corners.remove(p);
            }
            return;
        }
    }

    static void reduction(String view, Edges bare, Edges mounted) {
        var message = view + " bare=" + bare + ", mounted=" + mounted;
        if (bare == null || mounted == null) throw new AssertionError("Missing flash quad: " + message);
        float x = mounted.width / bare.width, y = mounted.height / bare.height;
        if (!(Math.abs(x-.2f)<.01f && Math.abs(y-.2f)<.01f))
            throw new AssertionError("RC1 should reduce both drawn edges to 0.2: " + message + ", ratios=" + x + "/" + y);
        bare.check(.75f); mounted.check(.75f*.2f);
        com.tacz.guns.GunMod.LOGGER.info("PORT_MUZZLE_SCALE: {} ratios={}/{}", message, x, y);
    }
}
