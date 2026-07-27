package com.tacz.guns.mixin.client;

import com.tacz.guns.client.animation.third.InnerThirdPersonManager;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 【第 42 轮：确认为永久废弃，不再尝试注册】
 *
 * <p>本 mixin 的作用是让<b>非玩家的人形生物</b>（僵尸、村民、骷髅等）持枪时也应用
 * TACZ 的第三人称手臂姿态。玩家自己的姿态由已注册的
 * {@code PlayerModelMixin}（注入 {@code setupAnim(AvatarRenderState)}）负责，不受影响。</p>
 *
 * <h2>为什么在 26.2 无法直接注册</h2>
 * 声明的注入点 {@code setupAnim(LivingEntity,FFFFF)V} 是 1.21.1 的旧签名。
 * 26.2 的 {@code HumanoidModel} 只剩两个重载（泛型签名核对）：
 * <pre>
 *   setupAnim(HumanoidRenderState)V     // 真正的实现
 *   setupAnim(Object)V                  // EntityModel 的桥接方法
 * </pre>
 * 直接注册会因找不到目标而<b>启动崩溃</b>。
 *
 * <h2>为什么也不改写成 {@code setupAnim(HumanoidRenderState)}</h2>
 * 改签名容易，但拿不到实体 —— 这是 26.2 render-state 架构的硬约束：
 * <ul>
 *   <li>{@code InnerThirdPersonManager#setRotationAnglesHead} 需要一个
 *       {@code LivingEntity}，用来读 {@code IGunOperator} 的开镜/换弹状态与主手物品；</li>
 *   <li>但 {@code HumanoidRenderState} 一路到根类 {@code EntityRenderState}
 *       都<b>既没有实体引用、也没有实体 ID</b>（逐字段核对：只有
 *       {@code entityType} / {@code x,y,z} / {@code ageInTicks} 这类快照数据）。
 *       {@code PlayerModelMixin} 能工作是因为 {@code AvatarRenderState}
 *       <b>额外</b>带了 {@code id} 字段，可以反查实体 —— 人形通用状态没有这个字段。</li>
 * </ul>
 * 要绕开就得自建「坐标 + 类型」反查实体的机制，既不可靠（同坐标多实体）
 * 又要在每帧渲染里做实体查找，性价比极低。
 *
 * <h2>影响范围很小</h2>
 * 枪械<b>物品本身</b>在非玩家人形手上仍会正常渲染 ——
 * 那是 {@code ItemInHandLayerMixin} 的职责，它注入的
 * {@code submit(...ArmedEntityRenderState...)} 覆盖所有人形生物，已注册且工作正常。
 * 本 mixin 缺失只影响<b>手臂摆放姿态</b>：僵尸拿枪会是原版的手臂姿势，
 * 而不是 TACZ 的持枪姿势。属于观感细节，不影响任何玩法。
 *
 * <p>保留源码仅作参考。若将来 Mojang 给 render state 补上实体引用，可据此重写。</p>
 */
@Mixin(HumanoidModel.class)
public class HumanoidModelMixin<T extends LivingEntity> {
    @Shadow
    @Final
    public ModelPart head;
    @Shadow
    @Final
    public ModelPart body;
    @Shadow
    @Final
    public ModelPart leftArm;
    @Shadow
    @Final
    public ModelPart rightArm;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At(value = "TAIL"))
    private void setRotationAnglesHead(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (ageInTicks == 0) {
            return;
        }
        InnerThirdPersonManager.setRotationAnglesHead(entityIn, rightArm, leftArm, body, head, limbSwingAmount);
    }
}
