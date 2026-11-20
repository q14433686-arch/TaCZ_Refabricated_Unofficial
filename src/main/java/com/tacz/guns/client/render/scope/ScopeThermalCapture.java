package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.DepthStencilState;
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
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

/** Replay only the solid body nodes from the ordinary world submit. No glow flag, name,
 * shadow, bounding box or second entity render. Alpha of the cutout draw = visible coverage.
 * The private depth copy is important: these are depth-writing materials. */
public final class ScopeThermalCapture {
    private static final TagKey<EntityType<?>> BLACKLIST = tag("blacklist"), WHITELIST = tag("whitelist");
    private static final Set<EntityRenderState> HOT = Collections.newSetFromMap(new IdentityHashMap<>());
    private static SubmitNodeStorage bodies;
    private static TextureTarget mask;
    private static boolean enabled, shaderPack, recording, ready, failed;

    private ScopeThermalCapture() {}

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "thermal_imaging/" + name));
    }

    static void beginFrame(boolean active) {
        if (!HOT.isEmpty()) HOT.clear();
        enabled = active && !failed;
        shaderPack = enabled && com.tacz.guns.compat.iris.IrisCompat.hasActiveIrisPack();
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
        recording = bodies != null && HOT.contains(state) && !com.tacz.guns.compat.iris.IrisCompat.isRenderShadow();
        return previous;
    }

    public static void endEntity(boolean previous) { recording = previous; }

    public static void recordSolid(SubmitNode node) {
        if (!recording) return;
        // Exotic model materials may explicitly draw through terrain. Only replay ordinary
        // depth-tested bodies; never turn a see-through material into a heat source.
        if (node instanceof ModelFeatureRenderer.Submit model) {
            DepthStencilState depth = model.renderType().pipeline().getDepthStencilState();
            if (depth == null || depth.depthTest() == com.mojang.blaze3d.platform.CompareOp.ALWAYS_PASS) return;
            // Some packs (Complementary FLICKERING_FIX) shift slime/chicken faces in clip
            // space. A steeper slope bias can't recover a face parallel to the camera. Try
            // that surface against the *actual* scene depth, EQUAL, without writing depth;
            // never widen the ordinary test or infer a hit from the pack filename.
            if (model.state() instanceof EntityRenderState state && (state.entityType == EntityTypes.SLIME
                    || state.entityType == EntityTypes.CHICKEN) && shaderPack) {
                var match = ScopeThermalMaterial.coverage(model, ScopeThermalMaterial.Depth.SHIFTED);
                if (match != model) bodies.order(-1).solid.submit(match);
            }
            node = ScopeThermalMaterial.coverage(model, shaderPack ? ScopeThermalMaterial.Depth.SCENE : ScopeThermalMaterial.Depth.NATIVE);
        }
        bodies.order(0).solid.submit(node);
    }

    static void finish(RenderTarget scene, CameraRenderState camera) {
        var storage = bodies;
        bodies = null;
        ready = false;
        if (storage == null || !enabled || scene.getDepthTexture() == null) return;
        // Replay vanilla cutout coverage into a private target, never a pack's gbuffer
        // program (which may have different outputs, alpha semantics and attachments).
        try (var program = com.tacz.guns.compat.iris.IrisSensorProgram.begin()) {
            var depth = scene.getDepthTexture();
            if (mask == null || mask.width != scene.width || mask.height != scene.height
                    || mask.getDepthTexture().getFormat() != depth.getFormat()) {
                if (mask != null) mask.destroyBuffers();
                mask = new TextureTarget("tacz_scope_heat", scene.width, scene.height, true, GpuFormat.RGBA8_UNORM);
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
                // 26.2 PreparedFrame creates its own render passes; steer those draws into
                // private attachments rather than executing a second pass against the scene.
                var previousColor = RenderSystem.outputColorTextureOverride;
                var previousDepth = RenderSystem.outputDepthTextureOverride;
                RenderSystem.outputColorTextureOverride = mask.getColorTextureView();
                RenderSystem.outputDepthTextureOverride = mask.getDepthTextureView();
                try {
                    encoder.clearColorTexture(mask.getColorTexture(), new Vector4f(0f));
                    try (var borrowed = shaderPack ? ThermalSceneDepth.borrow(scene) : null;
                         var frame = dispatcher.prepareFrame(storage)) { frame.executeSolid(); }
                } finally {
                    RenderSystem.outputColorTextureOverride = previousColor;
                    RenderSystem.outputDepthTextureOverride = previousDepth;
                }
            } finally { mv.popMatrix(); }
            ready = true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            failed = true;
            GunMod.LOGGER.warn("[TACZ Scope] Heat capture unavailable; using the optical lens.", ex);
            var player = Minecraft.getInstance().player;
            if (player != null) player.sendSystemMessage(net.minecraft.network.chat.Component
                    .translatable("message.tacz.scope.thermal_unavailable").withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }

    static @Nullable TextureTarget current() { return ready ? mask : null; }

    static void close() {
        beginFrame(false);
        ScopeThermalMaterial.clear();
        failed = false;
        if (mask != null) {
            try { mask.destroyBuffers(); }
            catch (RuntimeException ex) { GunMod.LOGGER.warn("[TACZ Scope] Could not release the heat target.", ex); }
            finally { mask = null; }
        }
    }
}
