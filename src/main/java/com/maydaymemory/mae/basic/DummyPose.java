package com.maydaymemory.mae.basic;

/**
 * Identity implementation of the legacy MAE pose contract used by TACZ.
 */
public class DummyPose implements Pose {
    public static final DummyPose INSTANCE = new DummyPose();

    private DummyPose() {
    }
}
