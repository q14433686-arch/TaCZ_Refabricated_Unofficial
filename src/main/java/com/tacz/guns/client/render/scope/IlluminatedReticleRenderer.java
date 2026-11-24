package com.tacz.guns.client.render.scope;

/** An illuminated mark can be part of a mixed division, not a replacement for its etched lines. */
public final class IlluminatedReticleRenderer implements IReticleRenderer {
    public static final IlluminatedReticleRenderer INSTANCE = new IlluminatedReticleRenderer();
    private IlluminatedReticleRenderer() {}

    @Override public boolean matches(ScopeNodeSet nodes) { return nodes.hasIlluminated(); }

    @Override public void submitReticle(Context ctx, ScopeNodeSet nodes) {
        ReticleGeometry.submit(ctx, nodes);
    }
}
