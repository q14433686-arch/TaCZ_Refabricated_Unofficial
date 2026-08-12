package com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation;

import com.maydaymemory.mae.basic.Pose;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * Legacy SimpleBedrockModel animation API shim retained by the 26.1.2 item bridge.
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
