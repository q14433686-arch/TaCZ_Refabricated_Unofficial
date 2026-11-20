package com.tacz.guns.api.client.animation;

import net.minecraft.resources.Identifier;

/** Immutable request; locator is resolved against the animated model, never a live deferred pose. */
public record AnimationParticleEvent(Identifier effect, String locator, String preEffectScript) {
    /** Fingerprint, not executable network content. Recipients look up their own canonical event. */
    public String token() {
        try {
            String canonical=effect+"\0"+(locator==null?"":locator)+"\0"+(preEffectScript==null?"":preEffectScript);
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
}
