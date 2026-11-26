package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.iris.IrisCompat;
import com.tacz.guns.compat.iris.IrisSensorProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage.ModelSubmit;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Collect actual posed model submissions, not projected entity bounds. 1.21.11 stores both
 * solid and sorted-alpha models through ModelFeatureRenderer.Storage.add. Replay their cutout
 * coverage with scene depth copied to a private writable target; never touch vanilla outlines. */
public final class ScopeThermalCapture {
    private static final TagKey<EntityType<?>> BLACKLIST = tag("blacklist"), WHITELIST = tag("whitelist");
    private static final Set<EntityRenderState> hot = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ArrayList<Body> draws = new ArrayList<>();
    private static boolean active, recording, ready, failed, shaderPack;
    private static TextureTarget mask;
    private static ByteBufferBuilder bytes;
    private static final Matrix4f worldView = new Matrix4f();
    // Retain counts after releasing the node list, for diagnostics without retaining model states.
    private static int submittedModels, texturedModels;
    public static void worldView(Matrix4f view) { worldView.set(view); }
    public static void finishWorld(RenderTarget scene) { finish(scene, worldView); }
    private record Body(ModelSubmit<?> model, RenderType type, boolean shifted) {}
    private ScopeThermalCapture() {}

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("tacz:thermal_imaging/" + name));
    }
    static void beginFrame(boolean enabled) {
        active = enabled && !failed;
        hot.clear(); draws.clear(); recording = ready = false;
        submittedModels = texturedModels = 0;
        shaderPack = active && IrisCompat.isUsingRenderPack();
    }
    public static void extracted(Entity entity, EntityRenderState state) {
        if (!active || state == null || !entity.isAlive() || entity.isInvisible() || entity.isSpectator()
                || entity == Minecraft.getInstance().getCameraEntity()) return;
        var type = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType());
        if (!type.is(BLACKLIST) && (entity instanceof LivingEntity || type.is(WHITELIST))) hot.add(state);
    }
    public static boolean beginEntity(EntityRenderState state) {
        boolean previous = recording;
        recording = active && !ready && hot.contains(state) && !IrisCompat.isRenderShadow();
        return previous;
    }
    public static void endEntity(boolean previous) { recording = previous; }
    public static void record(RenderType original, ModelSubmit<?> model) {
        if (!recording) return;
        submittedModels++;
        if (original.pipeline().getDepthTestFunction() == DepthTestFunction.NO_DEPTH_TEST) return;
        var texture = ScopeThermalMaterial.texture(original);
        if (texture == null) return;
        texturedModels++;
        if (shaderPack && model.state() instanceof EntityRenderState state &&
                (state.entityType == EntityType.CHICKEN || state.entityType == EntityType.SLIME))
            draws.add(new Body(model, ScopeThermalMaterial.Depth.SHIFTED.forTexture(texture), true));
        draws.add(new Body(model, (shaderPack ? ScopeThermalMaterial.Depth.SCENE : ScopeThermalMaterial.Depth.NATIVE).forTexture(texture), false));
    }

    public static void finish(RenderTarget scene, Matrix4f viewRotation) {
        if (!active || ready || failed || scene.getDepthTexture() == null) return;
        try (var ignored = IrisSensorProgram.begin()) {
            if (mask == null || mask.width != scene.width || mask.height != scene.height) {
                if (mask != null) mask.destroyBuffers();
                mask = new TextureTarget("tacz_scope_heat", scene.width, scene.height, true);
            }
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorTexture(mask.getColorTexture(), 0);
            if (!draws.isEmpty()) {
                mask.copyDepthFrom(scene);
                if (bytes == null) bytes = new ByteBufferBuilder(256 * 1024);
                var out = MultiBufferSource.immediate(bytes);
                var oldColor = RenderSystem.outputColorTextureOverride;
                var oldDepth = RenderSystem.outputDepthTextureOverride;
                var mv = RenderSystem.getModelViewStack();
                mv.pushMatrix();
                try (var borrowed = shaderPack ? ThermalSceneDepth.borrow(scene) : null) {
                    mv.set(viewRotation);
                    // Keep the bound world projection (including bob or the narrow pass), not HUD FOV.
                    RenderSystem.outputColorTextureOverride = mask.getColorTextureView();
                    RenderSystem.outputDepthTextureOverride = mask.getDepthTextureView();
                    for (var d : draws) if (d.shifted) replay(d.model, d.type, out);
                    out.endBatch();
                    for (var d : draws) if (!d.shifted) replay(d.model, d.type, out);
                    out.endBatch();
                } finally {
                    RenderSystem.outputColorTextureOverride = oldColor;
                    RenderSystem.outputDepthTextureOverride = oldDepth;
                    mv.popMatrix();
                }
            }
            ready = true;
        } catch (RuntimeException | ReflectiveOperationException e) {
            failed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Heat capture unavailable; retaining the ordinary optical lens", e);
        } finally { draws.clear(); }
    }
    private static <S> void replay(ModelSubmit<S> node, RenderType type, MultiBufferSource out) {
        var pose = new PoseStack(); pose.last().set(node.pose());
        var buffer = out.getBuffer(type);
        node.model().setupAnim(node.state());
        node.model().renderToBuffer(pose, node.sprite() == null ? buffer : node.sprite().wrap(buffer), node.lightCoords(), node.overlayCoords(), node.tintedColor());
    }
    static @Nullable TextureTarget current() { return ready ? mask : null; }
    static void close() {
        beginFrame(false); failed = false;
        if (mask != null) { mask.destroyBuffers(); mask = null; }
        if (bytes != null) { bytes.close(); bytes = null; }
        ScopeThermalMaterial.clear();
    }
}
