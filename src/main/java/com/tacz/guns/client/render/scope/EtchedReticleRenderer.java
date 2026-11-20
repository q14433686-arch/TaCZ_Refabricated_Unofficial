package com.tacz.guns.client.render.scope;

/** Etched divisions can contain opaque baffles. Never replay a full division without inverse clipping. */
public final class EtchedReticleRenderer implements IReticleRenderer {
    public static final EtchedReticleRenderer INSTANCE = new EtchedReticleRenderer();
    private EtchedReticleRenderer() {}

    @Override public boolean matches(ScopeNodeSet nodes) { return nodes.hasEtched() && !nodes.hasIlluminated(); }

    @Override public void submitReticle(Context ctx, ScopeNodeSet nodes) {
        ReticleGeometry.submit(ctx, nodes);
    }
}
