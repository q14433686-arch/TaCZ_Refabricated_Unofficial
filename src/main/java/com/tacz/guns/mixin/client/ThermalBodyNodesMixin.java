package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.render.scope.ScopeThermalCapture;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SubmitNodeCollection.class)
abstract class ThermalBodyNodesMixin {

    @Inject(method = "submitModel", at = @At("HEAD"))
    private <S> void tacz$recordModel(Model<? super S> model,
                                      S state,
                                      PoseStack poseStack,
                                      RenderType renderType,
                                      int lightCoords,
                                      int overlayCoords,
                                      int tintedColor,
                                      @Nullable TextureAtlasSprite sprite,
                                      int outlineColor,
                                      ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
                                      CallbackInfo ci) {
        ScopeThermalCapture.recordModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, sprite);
    }

    @Inject(method = "submitModelPart", at = @At("HEAD"))
    private void tacz$recordModelPart(ModelPart modelPart,
                                      PoseStack poseStack,
                                      RenderType renderType,
                                      int lightCoords,
                                      int overlayCoords,
                                      @Nullable TextureAtlasSprite sprite,
                                      boolean sheeted,
                                      boolean hasFoil,
                                      int tintedColor,
                                      ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay,
                                      int outlineColor,
                                      CallbackInfo ci) {
        ScopeThermalCapture.recordModelPart(modelPart, poseStack, renderType, lightCoords, overlayCoords, sprite, sheeted, tintedColor);
    }

    @Inject(method = "submitBlockModel", at = @At("HEAD"))
    private void tacz$recordBlockModel(PoseStack poseStack,
                                       RenderType renderType,
                                       List<BlockStateModelPart> parts,
                                       int[] tintLayers,
                                       int lightCoords,
                                       int overlayCoords,
                                       int outlineColor,
                                       CallbackInfo ci) {
        ScopeThermalCapture.recordBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords);
    }

    @Inject(method = "submitMovingBlock", at = @At("HEAD"))
    private void tacz$recordMovingBlock(PoseStack poseStack,
                                        MovingBlockRenderState state,
                                        CallbackInfo ci) {
        ScopeThermalCapture.recordMovingBlock(poseStack, state);
    }

    @Inject(method = "submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V", at = @At("HEAD"))
    private void tacz$recordItem(PoseStack poseStack,
                                 ItemDisplayContext displayContext,
                                 int lightCoords,
                                 int overlayCoords,
                                 int outlineColor,
                                 int[] tintLayers,
                                 List<BakedQuad> quads,
                                 ItemStackRenderState.FoilType foilType,
                                 CallbackInfo ci) {
        ScopeThermalCapture.recordItem(poseStack, lightCoords, overlayCoords, tintLayers, quads);
    }
}
