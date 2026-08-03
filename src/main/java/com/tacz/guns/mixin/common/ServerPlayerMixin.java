package com.tacz.guns.mixin.common;

import com.tacz.guns.api.entity.IGunOperator;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    /**
     * 重生后重置枪械操作状态。
     *
     * <h2>【务必先读】不要在这里清 {@code currentGunItem}</h2>
     *
     * <p>曾经为了修「跨维度换弹不生效」在此加过一行
     * {@code operator.getDataHolder().currentGunItem = null;}，
     * <b>那是基于错误前提的错误修复，已回退</b>。记录在此以免重蹈覆辙。</p>
     *
     * <p>当时的推理是：{@code currentGunItem} 是个 {@code Supplier}，
     * 捕获了构造时那个 {@code Inventory}；跨维度会重建 {@code ServerPlayer}，
     * 于是 supplier 指向孤儿背包。<b>这个前提在 26.2 下不成立</b>，
     * 字节码逐条确认：</p>
     * <ul>
     *   <li>跨维度走 {@code ServerPlayer#teleport(TeleportTransition)}，
     *       全程只调 {@code setServerLevel}，
     *       <b>同一个 ServerPlayer 实例、不重建</b>，
     *       {@code restoreFrom} 根本不参与；</li>
     *   <li>{@code restoreFrom} <b>只</b>由 {@code PlayerList#respawn}（死亡重生）调用；</li>
     *   <li>{@code Player#inventory} 是 {@code private final}，
     *       连重生时的新 {@code ServerPlayer} 也是在构造期定型，
     *       跨维度更不可能被换掉。</li>
     * </ul>
     *
     * <p>也就是说跨维度时那个 supplier <b>一直有效</b>，清空它纯属自伤：
     * {@code LivingEntityShoot#shoot} 首行即 {@code NOT_DRAW}，
     * 直到客户端补发 draw 才恢复 —— 表现为<b>跨维度后有一段时间无法操作枪械</b>，
     * 这正是该改动引入的回归。</p>
     *
     * <p>{@code initialData()} 有意不碰 {@code currentGunItem}，
     * 保持这个语义。重生时新玩家的 supplier 由客户端补发的 draw 重建，
     * 已有机制足够，无需在此干预。</p>
     */
    @Inject(method = "restoreFrom", at = @At("RETURN"))
    public void initialGunOperateData(ServerPlayer pThat, boolean pKeepEverything, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        IGunOperator.fromLivingEntity(player).initialData();
    }
}
