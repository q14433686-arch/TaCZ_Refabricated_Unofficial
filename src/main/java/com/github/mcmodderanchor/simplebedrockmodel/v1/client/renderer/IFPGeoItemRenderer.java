package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Legacy SimpleBedrockModel API shim used by TACZ's collector-backed item renderer.
 */
public interface IFPGeoItemRenderer {
    long getPutAwayDuration(ItemStack stack);

    @Nullable
    IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity);

    boolean isSameItem(ItemStack oldStack, ItemStack newStack);

    boolean blockOffhandRender();
}
