package com.tacz.guns.client.render.scope;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.tacz.guns.GunMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/** Replay only the solid body nodes from the ordinary world submit. No glow flag, name,
 * shadow, bounding box or second entity render. Alpha of the cutout draw = visible coverage.
 * The private depth copy is important: these are depth-writing materials. */
public final class ScopeThermalCapture {
    private static final TagKey<EntityType<?>> BLACKLIST = tag("blacklist"), WHITELIST = tag("whitelist");
    private static final Set<EntityRenderState> HOT = Collections.newSetFromMap(new IdentityHashMap<>());
    private static @Nullable SubmitNodeStorage bodies;
    private static @Nullable RenderBuffers thermalBuffers;
    private static @Nullable FeatureRenderDispatcher thermalDispatcher;
    private static @Nullable TextureTarget mask;
    private static boolean enabled, shaderPack, recording, hasRecorded, ready, replaying, failed;

    private ScopeThermalCapture() {}

    public static boolean isReplaying() {
        return replaying;
    }

    private static TagKey<EntityType<?>> tag(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "thermal_imaging/" + name));
    }

    static void beginFrame(boolean active) {
        if (!HOT.isEmpty()) HOT.clear();
        enabled = active && !failed;
        shaderPack = enabled && com.tacz.guns.compat.iris.IrisCompat.hasActiveIrisPack();
        recording = ready = hasRecorded = false;
        if (bodies != null) {
            bodies.clear();
        }
    }

    public static void extracted(Entity entity, EntityRenderState state) {
        if (!enabled || state == null || !entity.isAlive() || entity.isSpectator() || entity.isInvisible()
                || entity == Minecraft.getInstance().getCameraEntity()) return;
        var type = BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(entity.getType());
        if (!type.is(BLACKLIST) && (entity instanceof LivingEntity || type.is(WHITELIST))) HOT.add(state);
    }

    private static boolean ensureDispatcher() {
        if (bodies != null && thermalBuffers != null && thermalDispatcher != null) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        bodies = new SubmitNodeStorage();
        thermalBuffers = new RenderBuffers(1);
        thermalDispatcher = new FeatureRenderDispatcher(
                bodies,
                mc.getModelManager(),
                thermalBuffers.bufferSource(),
                mc.getAtlasManager(),
                thermalBuffers.outlineBufferSource(),
                thermalBuffers.crumblingBufferSource(),
                mc.font,
                mc.gameRenderer.getGameRenderState()
        );
        return true;
    }

    public static void beginWorldPass() {
        // If the narrow PIP scene already captured heat, don't collect the same models
        // a second time when the main scene renders immediately afterwards.
        if (enabled && !ready && !failed && ensureDispatcher()) {
            bodies.clear();
            hasRecorded = false;
        }
    }

    public static boolean beginEntity(EntityRenderState state) {
        boolean previous = recording;
        recording = enabled && !ready && !failed && bodies != null
                && HOT.contains(state) && !com.tacz.guns.compat.iris.IrisCompat.isRenderShadow();
        return previous;
    }

    public static void endEntity(boolean previous) {
        recording = previous;
    }

    private static boolean allowsDepth(RenderType renderType) {
        DepthStencilState depth = renderType.pipeline().getDepthStencilState();
        return depth != null && depth.depthTest() != CompareOp.ALWAYS_PASS;
    }

    private static ScopeThermalMaterial.Depth activeDepth() {
        return shaderPack ? ScopeThermalMaterial.Depth.SCENE : ScopeThermalMaterial.Depth.NATIVE;
    }

    public static <S> void recordModel(Model<? super S> model,
                                       S state,
                                       PoseStack poseStack,
                                       RenderType renderType,
                                       int lightCoords,
                                       int overlayCoords,
                                       int tintedColor,
                                       @Nullable TextureAtlasSprite sprite) {
        if (!recording || bodies == null || !allowsDepth(renderType)) return;
        recording = false;
        try {
            if (state instanceof EntityRenderState entityState
                    && (entityState.entityType == EntityType.SLIME || entityState.entityType == EntityType.CHICKEN)
                    && shaderPack) {
                RenderType match = ScopeThermalMaterial.coverageType(renderType, ScopeThermalMaterial.Depth.SHIFTED);
                if (match != renderType) {
                    bodies.order(-1).submitModel(model, state, poseStack, match, lightCoords, overlayCoords, tintedColor, sprite, 0, null);
                }
            }
            RenderType cov = ScopeThermalMaterial.coverageType(renderType, activeDepth());
            bodies.order(0).submitModel(model, state, poseStack, cov, lightCoords, overlayCoords, tintedColor, sprite, 0, null);
            hasRecorded = true;
        } finally {
            recording = true;
        }
    }

    public static void recordModelPart(ModelPart modelPart,
                                       PoseStack poseStack,
                                       RenderType renderType,
                                       int lightCoords,
                                       int overlayCoords,
                                       @Nullable TextureAtlasSprite sprite,
                                       boolean sheeted,
                                       int tintedColor) {
        if (!recording || bodies == null || !allowsDepth(renderType)) return;
        recording = false;
        try {
            RenderType cov = ScopeThermalMaterial.coverageType(renderType, activeDepth());
            bodies.order(0).submitModelPart(modelPart, poseStack, cov, lightCoords, overlayCoords, sprite, sheeted, false, tintedColor, null, 0);
            hasRecorded = true;
        } finally {
            recording = true;
        }
    }

    public static void recordBlockModel(PoseStack poseStack,
                                        RenderType renderType,
                                        List<BlockStateModelPart> parts,
                                        int[] tintLayers,
                                        int lightCoords,
                                        int overlayCoords) {
        if (!recording || bodies == null || !allowsDepth(renderType)) return;
        recording = false;
        try {
            RenderType cov = ScopeThermalMaterial.coverageType(renderType, activeDepth());
            bodies.order(0).submitBlockModel(poseStack, cov, parts, tintLayers, lightCoords, overlayCoords, 0);
            hasRecorded = true;
        } finally {
            recording = true;
        }
    }

    public static void recordMovingBlock(PoseStack poseStack, MovingBlockRenderState state) {
        if (!recording || bodies == null) return;
        recording = false;
        try {
            bodies.order(0).submitMovingBlock(poseStack, state);
            hasRecorded = true;
        } finally {
            recording = true;
        }
    }

    public static void recordItem(PoseStack poseStack,
                                  int lightCoords,
                                  int overlayCoords,
                                  int[] tintLayers,
                                  List<BakedQuad> quads) {
        if (!recording || bodies == null || quads.isEmpty()) return;
        recording = false;
        try {
            var depth = activeDepth();
            for (var quad : quads) {
                var info = quad.materialInfo();
                RenderType orig = info.itemRenderType();
                if (!allowsDepth(orig)) continue;
                int tintIndex = info.tintIndex();
                int color = tintIndex >= 0 && tintIndex < tintLayers.length ? tintLayers[tintIndex] : -1;
                RenderType cov = ScopeThermalMaterial.coverageType(orig, depth);
                bodies.order(0).submitCustomGeometry(poseStack, cov, (entry, out) -> {
                    var instance = new QuadInstance();
                    instance.setColor(color);
                    instance.setLightCoords(lightCoords);
                    instance.setOverlayCoords(overlayCoords);
                    out.putBakedQuad(entry, quad, instance);
                });
                hasRecorded = true;
            }
        } finally {
            recording = true;
        }
    }

    static void finish(RenderTarget scene, CameraRenderState camera) {
        ready = false;
        if (!enabled || failed || bodies == null || thermalDispatcher == null || thermalBuffers == null
                || scene.getDepthTexture() == null) {
            if (bodies != null) bodies.clear();
            hasRecorded = false;
            return;
        }
        try (var program = com.tacz.guns.compat.iris.IrisSensorProgram.begin()) {
            var depth = scene.getDepthTexture();
            if (mask == null || mask.width != scene.width || mask.height != scene.height
                    || mask.getDepthTexture() == null
                    || mask.getDepthTexture().getFormat() != depth.getFormat()) {
                if (mask != null) mask.destroyBuffers();
                mask = new TextureTarget("tacz_scope_heat", scene.width, scene.height, true);
            }
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            if (!hasRecorded) {
                try (var ignored = encoder.createRenderPass(() -> "tacz_scope_heat_empty",
                        mask.getColorTextureView(), OptionalInt.of(0))) { }
                bodies.clear();
                ready = true;
                return;
            }
            encoder.copyTextureToTexture(depth, mask.getDepthTexture(), 0, 0, 0, 0, 0, scene.width, scene.height);
            var mv = RenderSystem.getModelViewStack();
            mv.pushMatrix();
            try {
                mv.mul(camera.viewRotationMatrix);
                var previousColor = RenderSystem.outputColorTextureOverride;
                var previousDepth = RenderSystem.outputDepthTextureOverride;
                RenderSystem.outputColorTextureOverride = mask.getColorTextureView();
                RenderSystem.outputDepthTextureOverride = mask.getDepthTextureView();
                replaying = true;
                try {
                    try (var ignored = encoder.createRenderPass(() -> "tacz_scope_heat_clear",
                            mask.getColorTextureView(), OptionalInt.of(0))) { }
                    try (var borrowed = shaderPack ? ThermalSceneDepth.borrow(scene) : null) {
                        thermalDispatcher.renderAllFeatures();
                        thermalBuffers.bufferSource().endBatch();
                    }
                } finally {
                    replaying = false;
                    try {
                        bodies.endFrame();
                    } finally {
                        hasRecorded = false;
                        RenderSystem.outputColorTextureOverride = previousColor;
                        RenderSystem.outputDepthTextureOverride = previousDepth;
                    }
                }
            } finally {
                mv.popMatrix();
            }
            ready = true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            failed = true;
            if (bodies != null) bodies.clear();
            hasRecorded = false;
            GunMod.LOGGER.warn("[TACZ Scope] Heat capture unavailable; using the optical lens.", ex);
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .translatable("message.tacz.scope.thermal_unavailable")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        }
    }

    static @Nullable TextureTarget current() {
        return ready ? mask : null;
    }

    static void close() {
        beginFrame(false);
        ScopeThermalMaterial.clear();
        failed = false;
        if (mask != null) {
            try {
                mask.destroyBuffers();
            } catch (RuntimeException ex) {
                GunMod.LOGGER.warn("[TACZ Scope] Could not release the heat target.", ex);
            } finally {
                mask = null;
            }
        }
    }
}
