package com.tacz.guns.test;

import com.tacz.guns.client.particle.bedrock.particle.runtime.ParticleInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import java.util.IdentityHashMap;

/** Test-only: label frozen snapshots by definition, owner and view; measure the four vertices
 * when the deferred billboard callback runs. A smoke puff or tracer cannot stand in for flash. */
public final class MuzzleQuadProbe {
    static final Identifier FLASH = Identifier.parse("tacz:muzzle_flash");
    static final Identifier MARKER = Identifier.parse("port_test:muzzle_marker");
    static final Identifier SMOKE = Identifier.parse("tacz:muzzle_flash_smoke");
    private static final IdentityHashMap<ParticleInstance, Vector3f[]> corners = new IdentityHashMap<>();
    private static final Identifier ANCHOR_SMOKE = Identifier.parse("port_test:muzzle_anchor_smoke");
    static ParticleInstance markerFlash, markerSmoke;
    private static int owner = -1;
    private static boolean hand;
    static Edges last;
    static int smokeQuads, shots, modelLight, legacyTasks;

    record Edges(float width, float height, float spawnScale) {
        void check(float scale) {
            // Shipped formula: .45 * scale on either side of the center along each axis.
            if (!(Math.abs(width / spawnScale - .9f * scale) < .001f && Math.abs(height / spawnScale - .9f * scale) < .001f))
                throw new AssertionError("Wrong flash geometry " + this + " for pack scale " + scale);
        }
    }
    static void begin(int id, boolean first) {
        owner=id; hand=first; markerFlash=markerSmoke=null; last=null; smokeQuads=shots=legacyTasks=0; modelLight=-1; corners.clear();
    }
    static void stop() { begin(-1, false); }
    static void onShot(com.tacz.guns.api.event.common.GunFirePostEvent e) {
        if (e.getLogicalSide().isClient() && e.getShooter().getId()==owner) {
            shots++;
            modelLight=com.tacz.guns.client.particle.NativeMuzzleEffects.flashLight(e.getGunItemStack(), 0);
        }
    }
    public static void legacy() { if (owner >= 0) legacyTasks++; }

    public static void snapshot(ParticleInstance live, ParticleInstance frozen) {
        if (owner < 0 || live.emitter.isFPMode() != hand) return;
        var entity = live.emitter.getMolang().getContext().getEntity();
        if (!(entity instanceof Entity e) || e.getId() != owner) return;
        var id = live.emitter.getDefinition().getIdentifier();
        if (MARKER.equals(id)) markerFlash=live;
        if (ANCHOR_SMOKE.equals(id)) markerSmoke=live;
        if (FLASH.equals(id) || MARKER.equals(id)) corners.put(frozen, new Vector3f[4]);
        else if (SMOKE.equals(id) || ANCHOR_SMOKE.equals(id)) {
            if (!live.worldSpace || live.emitter.isFollowAnchor()) throw new AssertionError("Shipped smoke was not detached in world space");
            corners.put(frozen, new Vector3f[0]);
        }
    }
    public static void vertex(ParticleInstance p, float x, float y, float z) {
        if (owner < 0) return;
        var c = corners.get(p);
        if (c == null) return;
        if (c.length == 0) { smokeQuads++; corners.remove(p); return; }
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
