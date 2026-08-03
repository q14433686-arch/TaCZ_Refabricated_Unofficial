package com.tacz.guns.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.tacz.guns.client.renderer.other.HumanoidOffhandRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 对齐说明（对照 1.21.1 上游同名 mixin 与反编译的 {@code ItemInHandLayer}）。
 *
 * <p><b>澄清一个长期被误传的结论</b>：取消 {@code submitArmWithItem} <em>不会</em>让手臂消失。
 * 反编译源显示该方法只负责把<b>手里的物品</b>提交渲染（{@code item.submit(...)}），
 * 手臂本身由 {@code PlayerModel}/{@code HumanoidModel} 在实体模型阶段绘制，两者互不相干。
 * 因此把"第三人称手臂消失"归因于本 mixin 是不准确的；本 mixin 取消后真正丢失的是
 * <b>副手物品</b>，而这正是上游刻意为之（主手持枪时不渲染副手物品），并由
 * {@link HumanoidOffhandRender} 以"背在身上"的姿态补画回来。</p>
 *
 * <p>本轮修正的三处实际缺陷：</p>
 * <ol>
 *   <li><b>取消条件写错。</b> 旧代码判断 {@code arm == HumanoidArm.LEFT}，但 {@code LEFT}
 *       并不等于副手 —— 左利手玩家的主手就是 {@code LEFT}。上游用的是
 *       "主手持枪 &amp;&amp; 当前 arm 不是主手"。这里改为按 {@code state.mainArm} 判定，
 *       修复左利手玩家<b>主手枪械不渲染</b>的问题。</li>
 *   <li><b>{@code isSelf} 只置 false 从不置 true。</b> 上游在 {@code renderArmWithItem} 的 HEAD
 *       会对"渲染对象是本地玩家"置 {@code true}，旧移植把这段丢了，导致第三人称下
 *       抛壳/枪口火焰的自机判定恒为 false。已按上游补回。</li>
 *   <li><b>{@code HumanoidOffhandRender.renderGun} 是空实现。</b> 已按 26.2 的
 *       extract → submit 两段式重新实现，见该类注释。</li>
 * </ol>
 */
@Mixin(ItemInHandLayer.class)
public class ItemInHandLayerMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V", at = @At(value = "TAIL"))
    private void submitTail(PoseStack poseStack, SubmitNodeCollector collector, int packedLight, ArmedEntityRenderState state, float p_117185_, float p_117186_, CallbackInfo ci) {
        MuzzleFlashRender.isSelf = false;
        ShellRender.isSelf = false;
        HumanoidOffhandRender.renderGun(state, poseStack, collector, packedLight);
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "HEAD"), cancellable = true)
    private void submitArmWithItemHead(ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack itemStack, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        // 上游语义：渲染本地玩家时，开启自机抛壳/枪口火焰判定。
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && state instanceof AvatarRenderState avatarState && avatarState.id == minecraft.player.getId()) {
            MuzzleFlashRender.isSelf = true;
            ShellRender.isSelf = true;
        }

        // 上游语义：主手持枪时，取消“副手”物品的常规渲染，改由 HumanoidOffhandRender 以背挂姿态绘制。
        // 注意必须用 mainArm 判定副手，不能硬编码 LEFT（左利手玩家主手即为 LEFT）。
        ItemStack mainHand = state.getMainHandItemStack();
        if (mainHand != null && IGun.getIGunOrNull(mainHand) != null && arm != state.mainArm) {
            GunItemRendererWrapper.IS_MAIN_HAND_SUBMIT = false;
            ci.cancel();
            return;
        }

        // 【本轮修复：左利手玩家第三人称主手枪不渲染】
        //
        // 26.2 的 extractArmedEntityRenderState 是按<b>左右手</b>而不是按主副手填 display context 的
        // （字节码确认：右手固定 THIRD_PERSON_RIGHT_HAND、左手固定 THIRD_PERSON_LEFT_HAND）。
        // 而 GunItemRendererWrapper#renderByItem 沿用上游写法，见到 THIRD_PERSON_LEFT_HAND
        // 就当作「副手」直接 return。对左利手玩家，主手就是左手 ——
        // 上面的取消分支不会触发（arm == mainArm），可枪走到 renderByItem 又被 return 掉，
        // 于是<b>主手那把枪彻底不渲染</b>。
        //
        // 这里把「本次提交是不是主手」透传下去，让 renderByItem 用主副手而不是左右手判定。
        // 读写都在本方法的 HEAD/TAIL 之间同步完成（ItemStackRenderState#submit 是直调
        // SpecialModelRenderer#submit，无延迟队列），因此不会跨帧残留。
        GunItemRendererWrapper.IS_MAIN_HAND_SUBMIT = arm == state.mainArm;
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "TAIL"))
    private void submitArmWithItemTail(ArmedEntityRenderState state, ItemStackRenderState itemState, ItemStack itemStack, HumanoidArm arm, PoseStack poseStack, SubmitNodeCollector collector, int packedLight, CallbackInfo ci) {
        MuzzleFlashRender.isSelf = false;
        ShellRender.isSelf = false;
        GunItemRendererWrapper.IS_MAIN_HAND_SUBMIT = false;
    }
}
