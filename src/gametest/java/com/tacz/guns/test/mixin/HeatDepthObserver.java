package com.tacz.guns.test.mixin;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.tacz.guns.GunMod;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import com.tacz.guns.test.ScopeDepthProbe;
import com.tacz.guns.test.ShaderOpticSmoke;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.lwjgl.opengl.GL45C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the frame that owns the world depth, not the GUI framebuffer after swap. */
@Mixin(ScopeThermalCapture.class)
abstract class HeatDepthObserver {
    @Shadow private static TextureTarget mask;
    @Shadow private static boolean enabled;
    @Shadow private static boolean shaderPack;
    @Shadow private static boolean hasRecorded;
    @Shadow private static boolean ready;
    @Shadow private static boolean failed;

    @Unique private static int taczTest$diagLowCount;
    @Unique private static int taczTest$diagHighCount;

    @Inject(method = "extracted", at = @At("HEAD"))
    private static void observePose(Entity entity, EntityRenderState state, CallbackInfo ci) {
        if (ShaderOpticSmoke.poseTarget != null && ShaderOpticSmoke.poseTarget.equals(entity.getUUID()) && state instanceof LivingEntityRenderState living)
            ShaderOpticSmoke.renderedYaw = living.bodyRot;
    }

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
            GunMod.LOGGER.info("PORT_HEAT_DEPTH: {} centre={}, camera projection z={},{}; native compare=0x{}", sample, z[0],
                    camera.projectionMatrix.m22(), camera.projectionMatrix.m32(),
                    Integer.toHexString(GlConst.toGl(DepthStencilState.DEFAULT.depthTest())));
        } finally { for (int i=0; i<keys.length; i++) GL45C.glPixelStorei(keys[i], old[i]); }
    }

    @Inject(method = "finish", at = @At("RETURN"))
    private static void taczTest$afterFinish(RenderTarget scene, CameraRenderState camera, CallbackInfo ci) {
        if (!enabled || !shaderPack || scene == null || mask == null) return;
        if (taczTest$diagLowCount >= 12 && taczTest$diagHighCount >= 4) return;
        if (GL45C.glGetInteger(GL45C.GL_PIXEL_PACK_BUFFER_BINDING) != 0) return;
        int[] keys = {GL45C.GL_PACK_SKIP_PIXELS, GL45C.GL_PACK_SKIP_ROWS, GL45C.GL_PACK_ROW_LENGTH, GL45C.GL_PACK_ALIGNMENT};
        int[] old = java.util.Arrays.stream(keys).map(GL45C::glGetInteger).toArray();
        try {
            for (int i = 0; i < keys.length; i++) GL45C.glPixelStorei(keys[i], i == 3 ? 4 : 0);
            int w = Math.min(128, Math.min(scene.width, mask.width));
            int h = Math.min(128, Math.min(scene.height, mask.height));
            int x0 = (scene.width - w) / 2;
            int y0 = (scene.height - h) / 2;
            float[] rgba = new float[w * h * 4];
            float[] sz = new float[w * h];
            float[] mz = new float[w * h];
            GL45C.glGetTextureSubImage(((GlTexture) mask.getColorTexture()).glId(), 0, x0, y0, 0,
                    w, h, 1, GL45C.GL_RGBA, GL45C.GL_FLOAT, rgba);
            GL45C.glGetTextureSubImage(((GlTexture) scene.getDepthTexture()).glId(), 0, x0, y0, 0,
                    w, h, 1, GL45C.GL_DEPTH_COMPONENT, GL45C.GL_FLOAT, sz);
            GL45C.glGetTextureSubImage(((GlTexture) mask.getDepthTexture()).glId(), 0, x0, y0, 0,
                    w, h, 1, GL45C.GL_DEPTH_COMPONENT, GL45C.GL_FLOAT, mz);
            int hot = 0, diffZ = 0;
            float maxAbsDiff = 0f;
            for (int i = 0; i < w * h; i++) {
                if (rgba[i * 4] > 0.5f) hot++;
                float dz = Math.abs(mz[i] - sz[i]);
                if (dz > 0f) {
                    diffZ++;
                    if (dz > maxAbsDiff) maxAbsDiff = dz;
                }
            }
            boolean isLow = hot < 200;
            if (isLow ? taczTest$diagLowCount >= 12 : taczTest$diagHighCount >= 4) return;
            if (isLow) taczTest$diagLowCount++; else taczTest$diagHighCount++;
            int cx = w / 2, cy = h / 2, ciIdx = cy * w + cx;
            GunMod.LOGGER.info(
                    "PORT_HEAT_DIAG: recorded={} ready={} failed={} size={}x{} hot128={} diffZ={} maxDz={} centreR={} centreSceneZ={} centreMaskZ={} fmt={} proj=({},{},{},{})",
                    hasRecorded, ready, failed, scene.width, scene.height, hot, diffZ, maxAbsDiff,
                    rgba[ciIdx * 4], sz[ciIdx], mz[ciIdx],
                    scene.getDepthTexture().getFormat(),
                    camera.projectionMatrix.m00(), camera.projectionMatrix.m11(),
                    camera.projectionMatrix.m22(), camera.projectionMatrix.m32());
        } catch (Throwable t) {
            GunMod.LOGGER.warn("PORT_HEAT_DIAG failed: {}", t.toString());
        } finally {
            for (int i = 0; i < keys.length; i++) GL45C.glPixelStorei(keys[i], old[i]);
        }
    }
}
