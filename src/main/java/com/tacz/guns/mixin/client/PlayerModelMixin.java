package com.tacz.guns.mixin.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.third.InnerThirdPersonManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {
    @Shadow
    @Final
    public ModelPart leftSleeve;
    @Shadow
    @Final
    public ModelPart rightSleeve;

    public PlayerModelMixin(ModelPart part) {
        super(part);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At(value = "TAIL"))
    private void setRotationAnglesTail(AvatarRenderState renderState, CallbackInfo ci) {
        // Inventory/first-person arm models are not an entity-animation tick. Only reset their
        // vanilla arm rotations. World-model extraction forwards even an empty hand; the optional
        // PAL adapter shares one update per entity tick with END_CLIENT_TICK (including offscreen).
        if (renderState.ageInTicks == 0F) {
            // 第 0 帧（第一人称手部渲染 / GUI 缩略模型）：只清除默认手臂旋转，
            // 不碰任何动画状态。持枪判断沿用上游语义。
            if (IGun.getIGunOrNull(renderState.getMainHandItemStack()) != null) {
                tacz$resetAll(this.rightArm);
                tacz$resetAll(this.leftArm);
            }
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(renderState.id) instanceof LivingEntity livingEntity) {
            InnerThirdPersonManager.setRotationAnglesHead(
                    livingEntity,
                    this.rightArm,
                    this.leftArm,
                    this.body,
                    this.head,
                    renderState.walkAnimationSpeed
            );
        }

        // 【第 8 轮】此处<b>刻意不再</b>同步袖子姿态。
        //
        // 症状：第三人称（含物品栏里那个缩略玩家模型）手部出现"多出一层、且与手臂错位"的皮肤。
        //
        // 根因：1.21.1 里 leftSleeve/rightSleeve 是 PlayerModel 的<b>兄弟</b>部件，
        //       不会自动跟随手臂，所以上游必须显式 `sleeve.copyFrom(arm)`。
        //       26.2 改成了<b>子</b>部件（反编译 PlayerModel 构造函数确认）：
        //           this.leftSleeve  = this.leftArm.getChild("left_sleeve");
        //           this.rightSleeve = this.rightArm.getChild("right_sleeve");
        //       子部件在渲染时会<b>自动继承父级变换</b>。
        //
        //       移植时保留了这次拷贝（写成 loadPose(arm.storePose())），
        //       等于把手臂的变换<b>又叠加了一遍</b>到袖子上 —— 袖子相对手臂偏移一倍，
        //       看起来就是"手部多层皮肤没对齐/多出一只残缺的手"。
        //
        // 另外，vanilla 的 PlayerModel#setupAnim 每帧只设置袖子的 visible，
        // 姿态完全交给父子继承，这里不应干预。
    }

    @Unique
    private void tacz$resetAll(ModelPart part) {
        part.xRot = 0.0F;
        part.yRot = 0.0F;
        part.zRot = 0.0F;
    }
}
