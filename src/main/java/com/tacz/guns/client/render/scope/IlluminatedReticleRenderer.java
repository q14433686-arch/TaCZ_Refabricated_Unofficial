package com.tacz.guns.client.render.scope;

/** Luminous or hybrid strategy; authored geometry is shared with the other strategy. */
public final class IlluminatedReticleRenderer implements IReticleRenderer {
    public static final IlluminatedReticleRenderer INSTANCE = new IlluminatedReticleRenderer();
    private IlluminatedReticleRenderer() {}

    @Override public boolean matches(ScopeNodeSet nodes) { return nodes.hasIlluminated(); }

    @Override public void submitReticle(Context ctx, ScopeNodeSet nodes) {
        ReticleGeometry.submit(ctx, nodes);
    }
}
