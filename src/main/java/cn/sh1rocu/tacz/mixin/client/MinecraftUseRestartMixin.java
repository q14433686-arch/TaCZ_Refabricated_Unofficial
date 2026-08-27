package cn.sh1rocu.tacz.mixin.client;

import me.xjqsh.lrtactical.client.input.UsePressGate;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 右键没松手时，不让 LRTactical 物品自动开始<b>第二次</b>使用。
 *
 * <p>完整的病因、字节码证据与时序论证见 {@link UsePressGate} 的类注释；
 * 这里只负责在原版重新发起使用之前把它掐掉。</p>
 *
 * <h2>为什么单独开一个 mixin，而不是塞进 {@code MinecraftMixin}</h2>
 * {@code MinecraftMixin} 里已经有一处 {@code startUseItem} 注入
 * （{@code tacz$callForgeUseInputEvent}，转发 Forge 的输入事件），
 * 注入点在那次调用的<b>中途</b>。本门禁要的是「压根不进入」，必须钉在 HEAD；
 * 两者语义不同、归属不同（一个是 TACZ 的兼容层，一个是 LR 的输入修复），
 * 分开写便于单独审阅与单独摘除 —— 删掉 mixins json 里这一行即可整体回退。
 *
 * <p>1.21.11 确认 {@code Minecraft#startUseItem()} 存在：本分支
 * {@code MinecraftMixin#tacz$callForgeUseInputEvent} 已经注入同一方法
 * （{@code InteractKey} 也直接调用 {@code mc.startUseItem()}）。
 * 目标是具名方法，不使用 javac 合成名。</p>
 *
 * <h2>为什么在 HEAD 而不是更晚</h2>
 * {@code startUseItem} 内部会走到 {@code MultiPlayerGameMode#useItem}，
 * 而 {@code ServerboundUseItemPacket} 是在那儿的 {@code startPrediction} 回调里
 * 构造并送出的（先于 {@code ItemStack#use}）。只有在 HEAD 取消，
 * 才能保证「本地不进入使用状态」与「不给服务端发包」同时成立。
 */
@Mixin(Minecraft.class)
public abstract class MinecraftUseRestartMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void lr$blockHeldUseRestart(CallbackInfo ci) {
        if (UsePressGate.shouldBlockRestart()) {
            ci.cancel();
        }
    }
}
