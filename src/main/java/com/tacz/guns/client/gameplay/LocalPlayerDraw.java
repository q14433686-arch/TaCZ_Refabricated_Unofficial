package com.tacz.guns.client.gameplay;

import cn.sh1rocu.tacz.api.LogicalSide;
import cn.sh1rocu.tacz.mixin.accessor.BlockableEventLoopAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.event.common.GunDrawEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.message.ClientMessagePlayerDrawGun;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.TimeUnit;

public class LocalPlayerDraw {
    private final LocalPlayerDataHolder data;
    private final LocalPlayer player;
    public boolean readyToDraw = false;

    public LocalPlayerDraw(LocalPlayerDataHolder data, LocalPlayer player) {
        this.data = data;
        this.player = player;
    }

    public void draw(ItemStack lastItem) {
        // 重置各种参数
        this.resetData();

        // 获取各种数据
        ItemStack currentItem = player.getMainHandItem();
        long drawTime = System.currentTimeMillis() - data.clientDrawTimestamp;
        IGun currentGun = IGun.getIGunOrNull(currentItem);
        IGun lastGun = IGun.getIGunOrNull(lastItem);

        // 计算 draw 时长和 putAway 时长
        if (drawTime >= 0) {
            drawTime = getDrawTime(lastItem, lastGun, drawTime);
        }
        long putAwayTime = Math.abs(drawTime);

        // 发包通知服务器
        if (Minecraft.getInstance().gameMode != null) {
            Minecraft.getInstance().gameMode.ensureHasSentCarriedItem();
        }
        ClientPlayNetworking.send(new ClientMessagePlayerDrawGun());
        GunDrawEvent.CALLBACK.invoker().post(new GunDrawEvent(player, lastItem, currentItem, LogicalSide.CLIENT));

        // 不处于收枪状态时才能收枪
        if (drawTime >= 0) {
            doPutAway(lastItem, putAwayTime);
        }

        // 异步放映抬枪动画
        if (currentGun != null) {
            doDraw(currentItem, putAwayTime);
            // 刷新配件数据
            AttachmentPropertyManager.postChangeEvent(player, currentItem);
        }
    }

    private void doDraw(ItemStack currentItem, long putAwayTime) {
        TimelessAPI.getGunDisplay(currentItem).ifPresent(display -> {
            // 取消预定中的 draw 行为
            if (data.drawFuture != null) {
                data.drawFuture.cancel(false);
            }
            // 根据 put away time 预定 draw 行为（仅播放音效，状态机的初始化为了保证一致性已经移动）
            data.drawFuture = LocalPlayerDataHolder.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
                ((BlockableEventLoopAccessor) Minecraft.getInstance()).tacz$submitAsync(() -> {
                    SoundPlayManager.stopPlayGunSound();
                    SoundPlayManager.playDrawSound(player, display);
                });
            }, putAwayTime, TimeUnit.MILLISECONDS);
        });
    }

    private void doPutAway(ItemStack lastItem, long putAwayTime) {
        if (BuiltinItemRendererRegistry.INSTANCE.get(lastItem.getItem()) instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            // Keep the old viewmodel alive in ItemInHandRenderer for the whole put-away window.
            // Without this, the Minecraft 26.2 hand renderer immediately switches to the new
            // main-hand stack, so the state machine's put_away animation has nothing to render.
            //
            // 为什么放在这里（而不是还原上游那两处被注释掉的调用）：
            //   * 消费端是 ItemInHandRendererMixin 的 WrapOperation →
            //     FirstPersonAnimationCompat#getMainRenderStack → KeepingItemRenderer#getCurrentItem，
            //     keep 窗口内它返回旧枪，于是本帧提交的仍是旧枪视模（put_away 由
            //     AnimationController 继续推进，stateMachine 已 exit 也不影响）；
            //   * 窗口过期后 getCurrentItem 回落 mainHandItem（新枪），needReInit 成立 →
            //     tryInit 触发 INPUT_DRAW，收枪→抬枪的先后次序由此恢复；
            //   * AnimateGeoItemRenderer#tryExit / GunItemRendererWrapper#tryExit 里那两行
            //     上游注释保持注释状态：**只能有一个调用点**，两处都开会重复调用 keep
            //     （虽然 keep 自带时间窗守卫，但语义重复且容易让后来者误判触发时机）。
            //
            // 判定条件对齐上游：只有旧枪的状态机确实初始化过（= 它此前一直在被渲染，
            // tryExit 里的 INPUT_PUT_AWAY 真的会触发）才开窗口。否则（刚进世界、第三人称
            // 下切枪、上一把枪的窗口未过期所以这把从没被画过）开出来的是「旧枪静止一瞬」
            // 的空窗口 —— 上游把 keep() 写在 isInitialized() 之内正是这个意思。
            if (renderer.hasInitializedStateMachine(lastItem)) {
                KeepingItemRenderer.getRenderer().keep(lastItem, putAwayTime);
            }
            renderer.tryExit(lastItem, putAwayTime);
        }
        TimelessAPI.getGunDisplay(lastItem).ifPresent(display -> {
            ((BlockableEventLoopAccessor) Minecraft.getInstance()).tacz$submitAsync(() -> {
                // 播放收枪音效
                SoundPlayManager.stopPlayGunSound();
                SoundPlayManager.playPutAwaySound(player, display);
            });
        });
    }

    private long getDrawTime(ItemStack lastItem, IGun lastGun, long drawTime) {
        if (BuiltinItemRendererRegistry.INSTANCE.get(lastItem.getItem()) instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            long putAwayTime = renderer.getPutAwayTime(lastItem);
            if (drawTime > putAwayTime) {
                drawTime = putAwayTime;
            }
            data.clientDrawTimestamp = System.currentTimeMillis() + drawTime;
        } else {
            drawTime = 0;
            data.clientDrawTimestamp = System.currentTimeMillis();
        }
        return drawTime;
    }

    private void resetData() {
        // 锁上状态锁
        data.lockState(operator -> operator.getSynDrawCoolDown() > 0);
        // 重置客户端的 shoot 时间戳
        data.isShootRecorded = true;
        data.clientShootTimestamp = -1;
        data.chargeProgress = 0;
        // 重置客户端瞄准状态
        data.clientIsAiming = false;
        data.clientAimingProgress = 0;
        LocalPlayerDataHolder.oldAimingProgress = 0;
        // 重置拉栓状态
        data.isBolting = false;
        // 更新切枪时间戳
        if (data.clientDrawTimestamp == -1) {
            data.clientDrawTimestamp = System.currentTimeMillis();
        }
    }
}
