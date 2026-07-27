package cn.sh1rocu.tacz.mixin.client;

import cn.sh1rocu.tacz.api.mixin.ChannelAccessHandleInjection;
import cn.sh1rocu.tacz.util.SoundConsumerStorage;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import com.mojang.blaze3d.audio.Library;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 【第 42 轮：已查明可修，但确认无收益，保持不注册】
 *
 * <p>本 mixin 与 {@code ChannelAccessHandleMixin} 是一对，
 * 从 Kilt 移植而来，用于在声音真正绑定到 OpenAL 声道时发出
 * {@code PlaySoundSourceEvent}（可用于对特定音效做声道级处理，如自定义混响）。</p>
 *
 * <h2>26.2 的注入点已定位（若将来要修，直接用）</h2>
 * 源码里用的是 Yarn 中间名，在官方映射下不存在。已核对出真名：
 * <table border="1">
 *   <tr><th>源码写的</th><th>26.2 真名</th></tr>
 *   <tr><td>{@code method_19757}</td>
 *       <td>{@code lambda$play$1(ChannelHandle,SoundBuffer)V}<br>
 *           与 {@code lambda$play$3(ChannelHandle,AudioStream)V}</td></tr>
 *   <tr><td>{@code ChannelAccessHandleMixin} 的 {@code method_19737}</td>
 *       <td>{@code lambda$execute$0(Consumer)V}</td></tr>
 * </table>
 * 目标类 {@code SoundEngine}、方法 {@code play}、
 * {@code ChannelAccess$ChannelHandle#execute(Consumer)} 与其 {@code channel} 字段
 * 均确认存在。也就是说<b>技术上可以修好</b>。
 *
 * <h2>为什么仍然不修</h2>
 * {@code PlaySoundSourceEvent} 在<b>全仓零消费者</b> ——
 * 只有 {@code ChannelAccessHandleMixin} 自己 {@code invoker().post(...)} 发事件，
 * 没有任何代码 {@code register(...)} 监听。核对上游 1.21.1 同样如此，
 * 说明这是移植 Kilt 时一并带过来的基础设施，从未被真正使用。
 *
 * <p>修好它 = 引入两个注入 vanilla 音频热路径的 mixin（还依赖 lambda 名，
 * 每次小版本都可能变），换来一个没人监听的事件。<b>纯负收益</b>。</p>
 *
 * <p>保留源码与上述真名记录，等哪天真有功能需要声道级音效控制时再启用。</p>
 */
@Environment(EnvType.CLIENT)
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    // From Kilt
    @Inject(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V", shift = At.Shift.AFTER))
    private void tacz$prepareChannelInfo(SoundInstance soundInstance, CallbackInfo ci, @Local ChannelAccess.ChannelHandle channelHandle, @Local Sound sound) {
        var injection = ((ChannelAccessHandleInjection) channelHandle);

        if (sound.shouldStream())
            injection.tacz$setPool(Library.Pool.STREAMING);
        else
            injection.tacz$setPool(Library.Pool.STATIC);

        injection.tacz$setSoundInstance(soundInstance);
        injection.tacz$setSoundEngine((SoundEngine) (Object) this);
    }

    // From Kilt
    @ModifyArg(method = "method_19757", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private static Consumer<Channel> tacz$storeSourceConsumer(Consumer<Channel> consumer) {
        SoundConsumerStorage.soundConsumerChannels.add(consumer);
        return consumer;
    }

    // 暂时用不到
    // From Kilt
/*    @ModifyArg(method = "method_19758", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"))
    private static Consumer<Channel> tacz$storeStreamConsumer(Consumer<Channel> consumer) {
        SoundConsumerStorage.soundConsumerChannels.add(consumer);
        return consumer;
    }*/
}
