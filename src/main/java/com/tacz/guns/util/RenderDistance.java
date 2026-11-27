package com.tacz.guns.util;

import cn.sh1rocu.tacz.compat.meshloader.render.ScreenRenderTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.config.client.RenderConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public final class RenderDistance {
    /**
     * Screen-owned item draw（Screen#render 之外的屏幕持有绘制，如枪匠桌预览 PIP）的嵌套深度。
     *
     * <p>【C2 · 上游 1.2.0（ScreenItemVboContext）语义】旧的「GUI 标记后 100ms 内」时间窗被
     * 精确的屏幕绘制作用域取代：界面打开期间世界先照常渲染，时间窗会把世界里的每一把枪
     * 都挡在 LOD 之外。26.2 的 Screen 内嵌 3D（背包玩家人偶、枪匠桌预览）的 submit 发生在
     * Screen 的 extract 窗口里（见 {@link ScreenRenderTracker}），而枪匠桌预览的真正绘制
     * 是 PIP——发生在 {@code Screen#render} 之后，{@link ScreenRenderTracker} 拦不到它，
     * 所以 PIP 渲染器要在绘制期间用 {@link #beginScreenItemDraw()} 打开这个作用域。</p>
     */
    private static int screenItemDrawDepth;

    public static boolean inRenderHighPolyModelDistance(PoseStack poseStack) {
        // 只有屏幕正在绘制的那一遍绕过 LOD；界面开着时世界先照常渲染，仍按相机距离算 LOD。
        if (isGuiRender()) {
            return true;
        }
        int distance = RenderConfig.GUN_LOD_RENDER_DISTANCE.get();
        if (distance <= 0) {
            return false;
        }
        Matrix4f matrix4f = poseStack.last().pose();
        float viewDistance = matrix4f.m30() * matrix4f.m30() + matrix4f.m31() * matrix4f.m31() + matrix4f.m32() * matrix4f.m32();
        return viewDistance < distance * distance;
    }

    /** 打开一个 screen-owned item draw 作用域（枪匠桌预览 PIP 等）。必须与 {@link #endScreenItemDraw()} 配对。 */
    public static void beginScreenItemDraw() {
        screenItemDrawDepth++;
    }

    public static void endScreenItemDraw() {
        if (screenItemDrawDepth > 0) screenItemDrawDepth--;
    }

    /**
     * 当前这次绘制是否属于 GUI 屏幕画面：正在 Screen 的 extract 窗口内
     * （{@link ScreenRenderTracker}），或在 screen-owned PIP 绘制内
     * （{@link #screenItemDrawDepth} &gt; 0）。
     *
     * <p>公开给 meshloader 的近距离全模豁免用：FIXED/HEAD 语境既出现在
     * 世界（展示台雕像、物品展示框、背枪）也出现在 GUI 预览（枪匠桌界面），
     * 只有非 GUI 的那一侧才允许按相机距离豁免顶点预算——否则高模会被
     * 全量画进 GUI 图标。作用域是精确的：枪匠桌开着时，世界里的展示框/雕像
     * 不再像旧 100ms 时间窗那样被误判回 collector。</p>
     */
    public static boolean isGuiRender() {
        return screenItemDrawDepth > 0 || ScreenRenderTracker.isExtractingScreen();
    }
}
