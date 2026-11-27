package com.tacz.guns.client.animation.third;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.other.ThirdPersonManager;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.compat.playeranimator.PlayerAnimatorCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

public class InnerThirdPersonManager {
    public static void setRotationAnglesHead(LivingEntity entityIn, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head, float limbSwingAmount) {
        // 上游 1.2.0 删除了「暂停即 return」：暂停菜单后面的第三人称玩家应保持持枪姿态。旧注释里的 StackOverflow
        // 来自 PlayerAnimator 在模型阶段播放；上游把播放挪出了模型阶段，本线 PAL 仍在这里更新，所以暂停时只跳过
        // PAL 这一支，纯数学的原版持枪姿态照常计算（无 PAL 时与上游表现一致）。
        if (Minecraft.getInstance().isPaused()) {
            if (PlayerAnimatorCompat.isInstalled()) return;
        } else if (PlayerAnimatorCompat.updatePlayerLayers(entityIn)) return;
        if (entityIn instanceof IGunOperator operator) {
            ItemStack mainHandItem = entityIn.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(mainHandItem);
            if (iGun == null) {
                return;
            }
            // 睡觉、爬梯、游泳、鞘翅飞行不播放第三人称动画
            if (entityIn.getPose() == Pose.SLEEPING || entityIn.onClimbable() || entityIn.isSwimming() || entityIn.getPose() == Pose.FALL_FLYING) {
                return;
            }

            TimelessAPI.getGunDisplay(mainHandItem).ifPresent(display ->
                    playVanillaAnimation(entityIn, rightArm, leftArm, body, head, operator, display));
        }
    }

    private static void playVanillaAnimation(LivingEntity entityIn, ModelPart rightArm, ModelPart leftArm, ModelPart body, ModelPart head, IGunOperator operator, GunDisplayInstance display) {
        String animation = display.getThirdPersonAnimation();
        float aimingProgress = operator.getSynAimingProgress();
        if (aimingProgress <= 0) {
            ThirdPersonManager.getAnimation(animation).animateGunHold(entityIn, rightArm, leftArm, body, head);
        } else {
            ThirdPersonManager.getAnimation(animation).animateGunAim(entityIn, rightArm, leftArm, body, head, aimingProgress);
        }
    }
}
