package cn.sh1rocu.tacz.compat.meshloader.render;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

/**
 * 「今まさに GUI 画面（Screen）的提取阶段を描画している瞬間」を検出するトラッカー。
 *
 * <h3>问题</h3>
 * {@code Minecraft.getInstance().screen != null}（＝菜单是否「开着」）
 * 用来判定「GUI 内嵌 3D 渲染（如背包玩家娃娃）需要走 GUI 预算/关闭 GPU 路径」时，
 * 菜单开着期间一直为 true，导致世界内无关渲染（地面掉落物 / 展示框物品 /
 * 他人手持的枪）也被 GUI 预算/GPU 关闭误伤。mesh 枪一多，菜单一开，
 * 全屏 mesh 枪会瞬间集体切到重路径，造成严重性能劣化。
 *
 * <h3>解决</h3>
 * 世界渲染与 GUI 渲染在同一帧内是<b>不同时机</b>。通过 Fabric API 的
 * {@link ScreenEvents#BEFORE_INIT} 捕捉每个 Screen 的创建，并为其注册
 * {@code beforeExtract}/{@code afterExtract} 回调，精确检测「此刻是否正在
 * Screen 的提取阶段内部」——只有真正正在提取 GUI 画面的瞬间才为 true，
 * 世界内无关渲染不受影响。
 *
 * <h3>26.1.2 纪元差异</h3>
 * <p>1211 源注册的是 {@code ScreenEvents.beforeRender(screen)}/{@code afterRender(screen)}。
 * 26.1.2 的 fabric-screen-api（0.155.2+26.1.2，源码核实）把 GUI 生命周期改成了
 * frame-graph 的「提取」语义：{@code beforeRender}/{@code afterRender} 工厂被移除，
 * 对应物是 {@link ScreenEvents#beforeExtract(Screen)}/{@link ScreenEvents#afterExtract(Screen)}
 * —— 二者底层是<b>同一个</b>事件（{@code fabric_getBeforeRenderEvent}/
 * {@code fabric_getAfterRenderEvent}），只是回调形态从「渲染」改名为「提取」。
 * 对本闸门而言这正是正确的窗口：GUI 内嵌 3D（物品模型提取）的 submit 全部发生在
 * extract 阶段，GPU 路径必须在这一窗口内拒收。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)。</p>
 */
public final class ScreenRenderTracker {

    private static volatile boolean renderingScreen = false;

    private ScreenRenderTracker() {
    }

    /**
     * 当前是否正在某个 Screen 的提取阶段（GUI 画面本身的提取，含背包玩家娃娃等内嵌
     * 3D 展示）执行中。
     *
     * <p>与 {@code Minecraft.getInstance().screen != null} 不同，这里只在真正提取
     * GUI 内容的「瞬间」为 true。</p>
     */
    public static boolean isRenderingScreen() {
        return renderingScreen;
    }

    /** 注册到 Fabric API 的 ScreenEvents。{@code onInitializeClient()} 中调用一次。 */
    public static void register() {
        ScreenEvents.BEFORE_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.beforeExtract(screen).register((scr, extractor, mouseX, mouseY, tickProgress) -> {
                renderingScreen = true;
            });
            ScreenEvents.afterExtract(screen).register((scr, extractor, mouseX, mouseY, tickProgress) -> {
                renderingScreen = false;
            });
        });
    }
}
