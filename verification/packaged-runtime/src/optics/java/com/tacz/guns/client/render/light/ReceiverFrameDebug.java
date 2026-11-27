package com.tacz.guns.client.render.light;

/** Probe-only access to the actual frozen field and admitted geometry, never modifies it. */
public final class ReceiverFrameDebug {
    public static String describe(WorldFlashFrame frame) {
        if(frame==null) return "no source";
        return "sources="+frame.sources+" terrain="+frame.draws.stream().map(d -> "patches="+d.patches().size()+" strength="+d.strength()).toList();
    }
    private ReceiverFrameDebug() {}
}
