package com.tacz.guns.client.event;

import com.tacz.guns.client.compat.RecipeViewerReloadBridge;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.network.CommonNetworkCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

@Environment(EnvType.CLIENT)
public class CommonNetworkCacheEvent {
    /**
     * 断开连接时清理客户端枪包缓存与索引。
     *
     * <h2>【排查记录·未修复】这里的 memory connection 豁免，疑为一族 bug 的根源</h2>
     *
     * <p>用户实测的现象（跨维度换弹不连续、重进存档打不出子弹）可用同一段话描述：</p>
     * <blockquote>
     * 首次进入存档不触发；同一次游戏进程内进过一次该存档后，再反复进入就会触发；
     * 但<b>交叉</b>进入两个不同存档反而不触发。
     * </blockquote>
     *
     * <p>下面这个 early return 与该描述高度吻合：<b>单人存档走的是 memory connection，
     * 于是退出时 {@code ClientIndexManager.clear()} 等三行<u>根本不执行</u></b>，
     * 客户端索引/缓存跨存档残留。</p>
     *
     * <p>而索引的唯一重建入口是 {@code ServerMessageSyncGunPack#doSync} →
     * {@code ClientIndexManager.reload()}，且它对单人也只在收到同步包时才跑。
     * 于是「残留的旧索引」与「新世界的实体」之间会出现一段不一致窗口。</p>
     *
     * <p>该窗口的可观测后果（见用户日志 17:38:48 与 17:39:03 两次跨维度）：
     * {@code EntityBulletRenderer#submit} 开头的
     * {@code TimelessAPI.getGunDisplay(...).isEmpty() -> return} 命中，
     * 曳光弹整段不渲染、连 TracerDebug 也不打印，形成 10~12 秒的日志空窗；
     * 反复进入同一维度时空窗更长。</p>
     *
     * <p><b>注意一个容易误读的点</b>：空窗期间弹号跳变 224/555，
     * 远超实际射击速率（约 4 发/秒 × 11 秒 ≈ 44 发）。
     * 因此这些实体 ID <b>不是</b>被"吞掉的射击"消耗的，
     * 不能据此断定服务端拒绝了射击 —— 我先前正是这样误判过一次。
     * TracerDebug 只在子弹<b>被实际渲染时</b>才打印（调用点在
     * {@code renderTracerAmmo} 内部），所以日志空窗只能证明"没渲染"。</p>
     *
     * <p><b>尚未修复，也未验证。</b>贸然去掉这个豁免会影响单人存档的缓存生命周期，
     * 需先加日志确认：跨维度/重进存档后 {@code GUN_DISPLAY} 是否为空、
     * {@code reload()} 何时被调用。此处已有过多次基于推理的错误修改，务必先取证。</p>
     */
    public static void onClientPlayerLoggingIn(ClientPacketListener handler, Minecraft client) {
        if (handler.getConnection() == null || handler.getConnection().isMemoryConnection()) {
            return;
        }
        RecipeViewerReloadBridge.clear();
        CommonAssetsManager.clearInstance();
        CommonNetworkCache.INSTANCE.clear();
        ClientIndexManager.clear();
    }
}