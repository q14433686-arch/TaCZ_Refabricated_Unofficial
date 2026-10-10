package com.tacz.guns.client.render.scope;

import net.minecraft.client.renderer.texture.TextureHandle;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Render-thread aliases for GPU resources owned by the mask target or font atlas.
 * TextureManager's 26.4 TextureResources closes its texture and view on replacement;
 * borrowed handles must therefore stay outside that owning registry.
 */
public final class BorrowedScopeTextures {
    private static final Map<Identifier, TextureHandle> HANDLES = new HashMap<>();

    private BorrowedScopeTextures() {}

    public static void put(Identifier id, TextureHandle handle) {
        HANDLES.entrySet().removeIf(entry -> entry.getValue().textureView().isClosed());
        HANDLES.put(id, handle);
    }

    public static TextureHandle get(Identifier id) {
        TextureHandle handle = HANDLES.get(id);
        if (handle != null && handle.textureView().isClosed()) {
            HANDLES.remove(id);
            return null;
        }
        return handle;
    }
}
