package com.tacz.guns.client.event;

import cn.sh1rocu.simplebedrockmodel.api.event.RenderTickEvent;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import cn.sh1rocu.tacz.compat.fabric.BuiltinItemRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

@Environment(EnvType.CLIENT)
public class TickAnimationEvent {
    public static void tickAnimation(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        TimelessAPI.getGunDisplay(mainHandItem).ifPresent(gunIndex -> {
            var animationStateMachine = gunIndex.getAnimationStateMachine();
            if (animationStateMachine == null) {
                return;
            }
            // 群组服切世界导致的特殊 BUG 处理，正常情况不会遇到此问题
            if (player.input == null) {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
                return;
            }
            boolean moving = player.input.getMoveVector().length() > 0.01;
            boolean aiming = com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player).isAim();
            if (!aiming && !player.isMovingSlowly() && player.isSprinting()) {
                // 如果玩家正在移动，播放移动动画，否则播放 idle 动画。
                // 26.2 注意：瞄准/使用物品等状态可能让 LocalPlayer#isMovingSlowly 为 true。
                // 上游 1.21.1 的状态机仍是在 WALK 状态内部再按 aimingProgress 选择 walk_aiming，
                // 因此这里不能因为“正在慢速移动”就直接发 IDLE；否则 ADS 移动永远进不了
                // walk_aiming 分支，视觉幅度会像普通持枪移动/待机在参与混合。
                animationStateMachine.trigger(GunAnimationConstant.INPUT_RUN);
            } else if (moving && (aiming || !player.isMovingSlowly())) {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_WALK);
            } else {
                animationStateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
            }
        });
    }

    public static void tickAnimation(RenderTickEvent event) {
        if (event.phase == RenderTickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack mainHandItem = player.getMainHandItem();
        if (BuiltinItemRendererRegistry.INSTANCE.get(mainHandItem.getItem()) instanceof AnimateGeoItemRenderer<?, ?> renderer) {
            // 如果物品不一样了，先尝试初始化状态机
            if (renderer.needReInit(mainHandItem)) {
                renderer.tryInit(mainHandItem, player, event.renderTickTime);
            }
            renderer.visualUpdate(mainHandItem);
        }
    }
}
