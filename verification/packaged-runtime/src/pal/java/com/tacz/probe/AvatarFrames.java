package com.tacz.probe;

import net.minecraft.client.model.geom.PartPose;

/** Frame observer links no PAL types: it is inert on clients without that optional mod. */
public final class AvatarFrames {
    public static int watched = Integer.MIN_VALUE, count;
    public static PartPose leftArm;
    private AvatarFrames() {}
}
