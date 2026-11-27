package cn.sh1rocu.tacz.compat.meshloader.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/**
 * 「此刻是否正在执行 Screen 的提取（extract）阶段」的追踪器。
 *
 * <h2>为什么不能用 {@code minecraft.screen != null}</h2>
 *
 * <p>菜单开着不代表此刻正在提取 GUI；世界提取也会在菜单打开时运行。
 * 用 Fabric 的 {@link ScreenEvents} 精确框住 Screen 提取窗口。
 * {@code RenderDistance.isGuiRender()} 现在把本窗口与延迟 PIP 物品绘制的
 * 显式作用域合并，不再使用会泄漏到世界阶段的 100ms 时间窗。</p>
 *
 * <p>26.2 差异：Fabric API 26.2 的 Screen 事件把 render 窗口改成了 extract
 * 窗口（{@code beforeExtract}/{@code afterExtract}，对照 fabric-screen-api-v1
 * 26.2 分支源码），GUI 内嵌 3D（背包玩家人偶、枪匠桌预览）的 submit 恰好发生在
 * 这个窗口里 —— 语义与上游 1.21.1 的 render 窗口一一对应。</p>
 *
 * <p>世界 GPU 表（{@code PolyMeshGpuRenderer#WORLD_DRAWS}）用它挡住的事故是
 * 关 PR #33 的复刻版：Screen 内嵌 3D 预览的 submit 若落进世界表，要么被
 * 世界投影画到错误位置，要么因世界 pass 已消费而整层消失。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)，事件窗口按
 * 26.2 Fabric API 适配。</p>
 */
@Environment(EnvType.CLIENT)
public final class ScreenRenderTracker {

    private static volatile boolean extractingScreen = false;

    private ScreenRenderTracker() {
    }

    /** 此刻是否正在 Screen 的 extract 阶段内部（而不是「有菜单开着」）。 */
    public static boolean isExtractingScreen() {
        return extractingScreen;
    }

    /** 在 {@code TaczMeshyIntegration.onClientSetup()} 里调一次。 */
    public static void register() {
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.beforeExtract(screen).register((scr, graphics, mouseX, mouseY, tickProgress) ->
                    extractingScreen = true);
            ScreenEvents.afterExtract(screen).register((scr, graphics, mouseX, mouseY, tickProgress) ->
                    extractingScreen = false);
        });
    }
}
