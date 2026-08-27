package me.xjqsh.lrtactical.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import me.xjqsh.lrtactical.client.audio.DeafenState;
import me.xjqsh.lrtactical.client.audio.StunRingingSound;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 在 {@link AbstractSoundInstance#getVolume()} 的返回值上施加耳鸣衰减。
 *
 * <p>当前 26.1.2 引擎里，{@code SoundEngine#play} 直接走
 * {@code calculateVolume(float, SoundSource)}，不会经过
 * {@code calculateVolume(SoundInstance)}；因此把 mixin 挂在音效实例自己的
 * {@code getVolume()} 上，才能同时覆盖新播放、tick 更新与改滑条时的重算路径。</p>
 */
@Mixin(AbstractSoundInstance.class)
public class SoundInstanceVolumeMixin {
    @ModifyReturnValue(method = "getVolume()F", at = @At("RETURN"))
    private float lrtactical$applyDeafen(float original) {
        if ((Object) this instanceof StunRingingSound) {
            return original;
        }
        return original * DeafenState.getVolumeFactor(((SoundInstance) (Object) this).getSource());
    }
}
