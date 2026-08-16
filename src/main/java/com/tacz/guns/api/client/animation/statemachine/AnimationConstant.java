package com.tacz.guns.api.client.animation.statemachine;

/**
 * 这个类中定义了各种动画可能用到的数字常量。它们的存在是为了确保 AnimationStateContext
 * 中的方法使用的参数全部为基本类型，以方便脚本对它们的调用。
 */
public class AnimationConstant {
    /*
     * Intentionally empty upstream extension point. LuaAnimationConstant reflects public
     * fields from this class; TACZ currently defines all concrete gun-state constants in
     * GunAnimationConstant instead, so the resulting base library has no entries.
     */
}
