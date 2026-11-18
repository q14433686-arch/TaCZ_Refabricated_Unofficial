package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.tacz.guns.GunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/** Replay only the solid body nodes from the ordinary world submit. No glow flag, name,
 * shadow, bounding box or second entity render. Alpha of the cutout draw = visible coverage.
 * The private depth copy is important: these are depth-writing materials. */
public final class ScopeThermalCapture {
    private static final TagKey<EntityType<?>> BLACKLIST = tag("blacklist"), WHITELIST = tag("whitelist");
    private static final Set<EntityRenderState> HOT = Collections.newSetFromMap(new IdentityHashMap<>());
    private static SubmitNodeStorage bodies;
    private static TextureTarget mask;
    private static boolean enabled, recording, ready, failed;

    private ScopeThermalCapture() {}

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "thermal_imaging/" + name));
    }

    static void beginFrame(boolean active) {
        if (!HOT.isEmpty()) HOT.clear();
        enabled = active && !failed;
        bodies = null;
        recording = ready = false;
    }

    public static void extracted(Entity entity, EntityRenderState state) {
        if (!enabled || state == null || !entity.isAlive() || entity.isSpectator() || entity.isInvisible()
                || entity == Minecraft.getInstance().getCameraEntity()) return;
        var type = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType());
        if (!type.is(BLACKLIST) && (entity instanceof LivingEntity || type.is(WHITELIST))) HOT.add(state);
    }

    public static void beginWorldPass() {
        // If the narrow PIP scene already captured heat, don't collect the same models
        // a second time when the main scene renders immediately afterwards.
        bodies = enabled && !ready && !failed ? new SubmitNodeStorage() : null;
    }

    public static boolean beginEntity(EntityRenderState state) {
        boolean previous = recording;
        recording = bodies != null && HOT.contains(state);
        return previous;
    }

    public static void endEntity(boolean previous) { recording = previous; }

    public static void recordSolid(SubmitNode node) {
        if (!recording) return;
        // Exotic model materials may explicitly draw through terrain. Only replay ordinary
        // depth-tested bodies; never turn a see-through material into a heat source.
        if (node instanceof ModelFeatureRenderer.Submit model) {
            DepthStencilState depth = model.renderType().pipeline().getDepthStencilState();
            if (!DepthStencilState.DEFAULT.equals(depth)) return;
        }
        bodies.order(0).solid.submit(node);
    }

    static void finish(RenderTarget scene, CameraRenderState camera) {
        var storage = bodies;
        bodies = null;
        ready = false;
        if (storage == null || !enabled || scene.getDepthTexture() == null) return;
        try {
            var depth = scene.getDepthTexture();
            if (mask == null || mask.width != scene.width || mask.height != scene.height
                    || mask.getDepthTexture().getFormat() != depth.getFormat()) {
                if (mask != null) mask.destroyBuffers();
                mask = new TextureTarget("tacz_scope_heat", scene.width, scene.height, GpuFormat.RGBA8_UNORM, depth.getFormat());
            }
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            if (storage.order(0).solid.isEmpty()) {
                // No eligible body in the frustum: the sensor still shows a cold scene.
                // Don't copy a full depth image or prepare another feature frame for that.
                try (var ignored = encoder.createRenderPass(() -> "tacz_scope_heat_empty",
                        mask.getColorTextureView(), Optional.of(new Vector4f(0f)))) { }
                ready = true;
                return;
            }
            encoder.copyTextureToTexture(depth, mask.getDepthTexture(), 0, 0, 0, 0, 0, scene.width, scene.height);
            var mv = RenderSystem.getModelViewStack();
            mv.pushMatrix();
            try {
                mv.mul(camera.viewRotationMatrix);
                var dispatcher = Minecraft.getInstance().gameRenderer.featureRenderDispatcher();
                try (var frame = dispatcher.prepareFrame(storage);
                     var pass = encoder.createRenderPass(() -> "tacz_scope_heat", mask.getColorTextureView(),
                             Optional.of(new Vector4f(0f)), mask.getDepthTextureView(), OptionalDouble.empty())) {
                    frame.executeSolid(pass);
                }
            } finally { mv.popMatrix(); }
            ready = true;
        } catch (RuntimeException ex) {
            failed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Heat capture unavailable; using the optical lens.", ex);
        }
    }

    static @Nullable TextureTarget current() { return ready ? mask : null; }

    static void close() {
        beginFrame(false);
        failed = false;
        if (mask != null) {
            try { mask.destroyBuffers(); }
            catch (RuntimeException ex) { GunMod.LOGGER.warn("[TACZ Scope] Could not release the heat target.", ex); }
            finally { mask = null; }
        }
    }
}
