package me.xjqsh.lrtactical.init;

import me.xjqsh.lrtactical.EquipmentMod;
import me.xjqsh.lrtactical.effect.HarmfulEffect;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;

/**
 * 状态效果注册 —— 闪光弹的致盲与耳鸣。
 *
 * <h2>与 NeoForge 版的差异</h2>
 * 上游用 {@code DeferredRegister} + {@code DeferredHolder}；
 * Fabric 直接 {@code Registry.register}，沿用本仓库既有模式。
 *
 * <p><b>为什么要持有 {@link Holder} 而不只是 {@code MobEffect}</b>：
 * 26.2 的 {@code MobEffectInstance} 构造器与 {@code LivingEntity#getEffect}
 * 全部接收 {@code Holder<MobEffect>}（字节码确认），
 * 而 {@code Registry.register} 返回的是裸对象。
 * 因此注册后立刻取一次 Holder 存起来，避免每个调用点都去查表。
 *
 * <p><b>{@code init()} 必须被显式调用</b> —— Fabric 无 DeferredRegister，
 * 静态字段的注册要靠类加载触发，而类加载是惰性的。
 */
public final class ModEffects {
    /** 致盲：屏幕糊白/黑。实际表现见 {@code BlindnessOverlay}。 */
    public static final Holder<MobEffect> BLIND = register("blinded", new HarmfulEffect(0xFFFFFF));

    /** 耳鸣/失聪：压低所有音效音量。实际表现见 {@code DeafenSoundHandler}。 */
    public static final Holder<MobEffect> DEAFENED = register("deafened", new HarmfulEffect(0xFFFFFF));

    private ModEffects() {
    }

    public static void init() {
        // 触发静态初始化，完成注册
    }

    private static Holder<MobEffect> register(String name, MobEffect effect) {
        Identifier id = Identifier.fromNamespaceAndPath(EquipmentMod.MOD_ID, name);
        Registry.register(BuiltInRegistries.MOB_EFFECT, id, effect);
        // 26.2: Registry#getHolder(Identifier) 已改名为 get(Identifier)，
        // 返回 Optional<Holder.Reference<T>>（字节码确认）。
        // 刚注册过，orElseThrow 不会触发；真触发说明注册出了问题，应当尽早暴露。
        return BuiltInRegistries.MOB_EFFECT.get(id).orElseThrow();
    }
}
