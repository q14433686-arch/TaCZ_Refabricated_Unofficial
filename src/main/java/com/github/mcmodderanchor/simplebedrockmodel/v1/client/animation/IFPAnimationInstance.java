package com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation;

import com.maydaymemory.mae.basic.Pose;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * Minimal ABI compatibility surface for the unavailable 26.2 SimpleBedrockModel build.
 * AnimateGeoItemRenderer supplies a real implementation backed by TACZ's state machine.
 */
public interface IFPAnimationInstance {
    ItemStack currentItem();

    Pose getPose();

    void tick(float v);

    @NotNull Quaternionf getCameraRotation();

    void setCameraRotation(@NotNull Quaternionf quaternionf);

    Pose getCachedPose();

    void updateItem(ItemStack itemStack);

    void triggerDraw();

    void triggerPutAway();
}
