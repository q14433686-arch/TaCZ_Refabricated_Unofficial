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
        // 【第 34 轮修复】收枪后 PAL 第三人称动作卡死在最后一帧。
        //
        // 症状：装了 Player Animation Library 时，持枪的第三人称动作正确；但一切换到
        // 非枪械物品或空手，玩家就永远保持上一次持枪的姿态（站立/走路/奔跑/趴姿都会卡住）。
        //
        // 根因是<b>这里的提前 return 把"停止动画"的路一起堵死了</b>。
        // 真正负责停 PAL 的是 InnerThirdPersonManager#setRotationAnglesHead 的头几行：
        //     IGun iGun = IGun.getIGunOrNull(mainHandItem);
        //     if (iGun == null) { PlayerAnimatorCompat.stopAllAnimation(entityIn); return; }
        // 也就是说"手里不是枪"恰恰是<b>必须把调用送进去</b>的情况 —— 只有进去了才会
        // 触发 stopAllAnimation。而旧代码在 mixin 层就 `if (getIGunOrNull == null) return;`，
        // 于是收枪那一刻起 InnerThirdPersonManager 再也不会被调用一次，
        // PAL 的四个 controller（LOWER/LOOP_UPPER/ONCE_UPPER/ROTATION）
        // 谁都没收到 fade-out 指令，就一直播着最后那个循环动画。
        //
        // 这也解释了用户描述的"趴着时比较特殊"：趴姿走的是 LIE/LIE_MOVE 动画，
        // 卡住后表现为"趴着的直立形态"，本质与其他状态同因。
        //
        // 修复：把持枪判断<b>下放</b>给 InnerThirdPersonManager 自己做，
        // 本方法只负责"有活体实体就转发"。stopAllAnimation 内部对四个 controller
        // 都有 `controller.isActive()` 守卫（PalAnimationManager#stop），
        // 重复调用是幂等的，不会每帧重复触发淡出。
        // 【第 36 轮修复】把上游的 `ageInTicks == 0` 守卫补回来。
        //
        // 上一轮为了修"收枪后动作卡死"，把整个提前 return 删了改成无条件转发，
        // <b>连同上游本来就有的这条守卫一起删掉了</b>（上游 HumanoidModelMixin L31-33：
        //     if (ageInTicks == 0) { return; }
        // ）。这一处遗漏同时造成了用户报告的两个新 bug：
        //
        // <b>① 第三人称持枪退出再进存档必崩。</b>
        // ageInTicks = tickCount + partialTick（EntityRenderer#extractRenderState 偏移 69-75
        // 字节码确认：读 Entity.tickCount 写入 EntityRenderState.ageInTicks）。
        // 刚进世界的第一帧 tickCount == 0，此时实体虽已加入 ClientLevel、
        // 却还没跑过任何一次 tick —— TACZ 的 ShooterDataHolder / AttachmentCacheProperty
        // 都是在 tick 里惰性初始化的，PAL 的 controller 也尚未由
        // ANIMATION_DATA_FACTORY 挂到玩家身上。这一帧就去跑完整动画链，
        // 会撞上一堆半初始化状态。上游那条守卫的<b>唯一作用</b>就是跳过这一帧。
        //
        // 为什么"第三人称 + 持枪"才触发：第一人称下 PlayerModel 不渲染本体，
        // 走不到这里；空手时 InnerThirdPersonManager 在 getIGunOrNull==null 处
        // 就 return 了，够不到后面的动画代码。两个条件缺一不可 —— 与用户实测完全吻合。
        //
        // <b>② 收枪后运动状态无持枪动作、开枪换弹时又短暂恢复。</b>
        // 这条守卫被删后，GUI 里那个缩略玩家模型（物品栏/背包预览，ageInTicks 恒为 0）
        // 每帧也会进来跑一遍 stopAllAnimation。它和世界里的真实玩家<b>共用同一个
        // PAL controller</b>（controller 按玩家实体查，不区分渲染场合），
        // 于是背包预览每帧都在把刚播上的循环动画淡出掉 ——
        // 表现就是走/跑/游泳的持枪动作起不来；
        // 而开枪/换弹走的是 ONCE_UPPER 的 triggerAnimation（一次性、优先级更高），
        // 能抢在被淡出前放完，所以"短暂恢复后又消失"。
        //
        // 修复后仍能修好上一轮那个"收枪卡死"：ageInTicks != 0 的正常渲染帧
        // 照样会转发给 InnerThirdPersonManager，由它在 iGun == null 时调用
        // stopAllAnimation —— 停止逻辑没有丢，只是不再被第 0 帧和 GUI 预览帧误触发。
        // <b>为什么是"两段"而不是一个 if/else</b>：上游其实把这两件事拆在<b>两个</b> mixin 里，
        // 各自有相反的守卫，26.2 因为 setupAnim 签名合并（都变成
        // setupAnim(AvatarRenderState)）才落到同一个方法里：
        //   上游 PlayerModelMixin   : if (ageInTicks == 0 && 持枪) 只做手臂归零
        //   上游 HumanoidModelMixin : if (ageInTicks == 0) return; 之后才跑动画
        // 上一轮把两者揉成一段并删掉守卫，等于让动画逻辑也在第 0 帧跑了。
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
