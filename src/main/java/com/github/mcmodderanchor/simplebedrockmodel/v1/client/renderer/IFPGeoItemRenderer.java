package com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer;

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Minimal ABI compatibility surface from SimpleBedrockModel.
 *
 * <p>The upstream library has no 26.2 build, but TACZ's own renderer implements these
 * lifecycle methods and current call sites use them. “Minimal interface” does not mean
 * the methods are no-ops.</p>
 */
public interface IFPGeoItemRenderer {
    long getPutAwayDuration(ItemStack stack);

    @Nullable
    IFPAnimationInstance createAnimationInstance(ItemStack stack, Entity entity);

    boolean isSameItem(ItemStack oldStack, ItemStack newStack);

    boolean blockOffhandRender();
}
