package com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation;

import com.maydaymemory.mae.basic.Pose;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

/**
 * Stub for simplebedrockmodel IFPAnimationInstance (library not yet available for 26.2).
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
