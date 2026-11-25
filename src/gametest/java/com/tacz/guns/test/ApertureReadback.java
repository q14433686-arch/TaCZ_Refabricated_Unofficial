package com.tacz.guns.test;

import com.tacz.guns.client.render.scope.ScopeDepthCopyState;
import org.lwjgl.opengl.GL45C;

/** Read only the actual two GL textures used by the lens. Bounds can include a physical
 * rim; colour alone does not tell whether a diagnostic pixel was covered by the ocular. */
final class ApertureReadback {
    static boolean[] region(int x, int y, int side, int height, int width) {
        var aperture = ScopeDepthCopyState.apertureDepthTarget();
        var world = ScopeDepthCopyState.worldDepthTarget();
        if (!ScopeDepthCopyState.hasMaskCycleThisFrame() || !aperture.available() || !world.available()
                || aperture.width() != width || world.width() != width || aperture.height() != height || world.height() != height)
            throw new AssertionError("Stale or mismatched ocular depth textures");
        if (GL45C.glGetInteger(GL45C.GL_PIXEL_PACK_BUFFER_BINDING) != 0) throw new AssertionError("Unexpected pixel pack buffer");
        int[] keys = {GL45C.GL_PACK_SKIP_PIXELS, GL45C.GL_PACK_SKIP_ROWS, GL45C.GL_PACK_ROW_LENGTH, GL45C.GL_PACK_ALIGNMENT};
        int[] old = java.util.Arrays.stream(keys).map(GL45C::glGetInteger).toArray();
        try {
            for (int i=0; i<keys.length; i++) GL45C.glPixelStorei(keys[i], i==3 ? 4 : 0);
            var a = depth(aperture.textureId(), x, height-y-side, side);
            var w = depth(world.textureId(), x, height-y-side, side);
            boolean[] mask = new boolean[a.length];
            for (int row=0;row<side;row++) for (int col=0;col<side;col++) {
                int src=(side-row-1)*side+col;
                mask[row*side+col] = a[src] < w[src] - 1e-6f;
            }
            return mask;
        } finally { for (int i=0; i<keys.length; i++) GL45C.glPixelStorei(keys[i], old[i]); }
    }
    private static float[] depth(int texture, int x, int y, int side) {
        float[] buffer = new float[side*side];
        GL45C.glGetTextureSubImage(texture,0,x,y,0,side,side,1,GL45C.GL_DEPTH_COMPONENT,GL45C.GL_FLOAT,buffer);
        return buffer;
    }
    private ApertureReadback() {}
}
