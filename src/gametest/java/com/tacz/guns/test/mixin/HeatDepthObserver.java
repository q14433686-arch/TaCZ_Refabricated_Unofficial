package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import com.tacz.guns.test.ScopeDepthProbe;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.lwjgl.opengl.GL45C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the frame that owns the world depth, not the GUI framebuffer after swap. */
@Mixin(ScopeThermalCapture.class)
abstract class HeatDepthObserver {
    @Inject(method = "finish", at = @At("HEAD"))
    private static void taczTest$sceneDepth(RenderTarget scene, CameraRenderState camera, CallbackInfo ci) {
        String sample = ScopeDepthProbe.next;
        if (sample == null) return;
        ScopeDepthProbe.next = null;
        if (GL45C.glGetInteger(GL45C.GL_PIXEL_PACK_BUFFER_BINDING) != 0) throw new AssertionError("PBO bound during depth control");
        int[] keys = {GL45C.GL_PACK_SKIP_PIXELS, GL45C.GL_PACK_SKIP_ROWS, GL45C.GL_PACK_ROW_LENGTH, GL45C.GL_PACK_ALIGNMENT};
        int[] old = java.util.Arrays.stream(keys).map(GL45C::glGetInteger).toArray();
        try {
            for (int i=0; i<keys.length; i++) GL45C.glPixelStorei(keys[i], i==3 ? 4 : 0);
            float[] z = new float[1];
            GL45C.glGetTextureSubImage(((GlTexture)scene.getDepthTexture()).glId(), 0, scene.width/2, scene.height/2, 0,
                    1, 1, 1, GL45C.GL_DEPTH_COMPONENT, GL45C.GL_FLOAT, z);
            GunMod.LOGGER.info("PORT_HEAT_DEPTH: {} centre={}, camera projection z={},{}", sample, z[0],
                    camera.projectionMatrix.m22(), camera.projectionMatrix.m32());
        } finally { for (int i=0; i<keys.length; i++) GL45C.glPixelStorei(keys[i], old[i]); }
    }
}
