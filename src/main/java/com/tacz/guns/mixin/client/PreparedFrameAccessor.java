package com.tacz.guns.mixin.client;

import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读一下 {@code PreparedFrame} 到底「在不在用」。
 *
 * <p>这个类没有对应的 getter，判据只能看那个私有字段：{@code begin()} 开头是
 * <pre>if (this.context != null) throw new IllegalStateException("PreparedFrame already in use");</pre>
 * 也就是说 <b>{@code context != null} 就等于「在用」</b>（26.2 字节码实读）。
 *
 * <p>为什么不干脆直接调 {@code close()} 试试、NPE 就吞掉：{@code close()} 开头确实是
 * {@code Objects.requireNonNull(this.context, "Frame not in use")}，抛在任何副作用之前，
 * 所以那样写也不会弄坏什么。但那是拿异常当条件判断 —— 这里要的判断本来就只有一个
 * 字段可读，读它就好。
 *
 * @see FeatureRenderDispatcherMixin
 */
@Mixin(FeatureRenderDispatcher.PreparedFrame.class)
public interface PreparedFrameAccessor {

    /** @return 本帧的上下文；{@code null} 表示这个 frame 当前没在用 */
    @Accessor("context")
    FeatureFrameContext tacz$context();
}
