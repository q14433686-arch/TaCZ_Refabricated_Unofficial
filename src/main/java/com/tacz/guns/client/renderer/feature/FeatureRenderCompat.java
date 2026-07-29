package com.tacz.guns.client.renderer.feature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import javax.annotation.Nullable;

public final class FeatureRenderCompat {

    private FeatureRenderCompat() {
    }

    public static boolean submit(PoseStack poseStack, ItemDisplayContext transformType,
                                 BedrockModel model, RenderType renderType,
                                 int light, int overlay,
                                 int r, int g, int b, int a,
                                 Object collector) {
        return false;
    }
}
